package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * 组织子树缓存协调器：代际（generation）+ cache-aside（G2-TAIL）+ 命名空间隔离（C1）。
 * <p>
 * <b>C1</b>：带 {@link GenerationStampedSet} 的值只写入
 * {@link RedisKeyConstants#DEPT_CHILDREN_ID_LIST_V2}，绝不写入遗留
 * {@link RedisKeyConstants#DEPT_CHILDREN_ID_LIST}。滚动升级时旧实例只访问旧名，
 * 不会对共享 Redis 中的新类型做反序列化。
 * <p>
 * <b>G2-TAIL</b>：写成功 {@link #bumpGenerationAndEvict()}；读路径
 * {@link #getIfFresh}/{@link #putIfGeneration} 用代际拒绝尾随旧 put。
 * <p>
 * 部署/回滚要点：
 * <ul>
 *   <li>滚动发布：新旧包可并存；旧包读旧名（Set），新包读写 V2（stamped）。</li>
 *   <li>回滚应用：停新包后仅旧包读旧名；V2 键可残留至 TTL，旧包不访问，无 SerializationException。</li>
 *   <li>组织写成功时顺带 clear 旧名，降低滚动窗口内旧实例陈旧 Set 的存活时间。</li>
 * </ul>
 */
@Component
@Slf4j
public class DeptChildrenCacheInvalidator {

    private static final String REDIS_GEN_KEY = "system:dept:children:gen";

    @Resource
    private ObjectProvider<CacheManager> cacheManagerProvider;
    @Resource
    private ObjectProvider<RedissonClient> redissonClientProvider;

    /** 单测 / 无 Redisson 时的进程内代际 */
    private final AtomicLong localGeneration = new AtomicLong(0L);

    /**
     * 测试钩子：DB 加载完成、尝试 put 之前调用（参数为 loadGeneration）。
     */
    @VisibleForTesting
    volatile LongConsumer afterLoadBeforePutHook;

    /** 当前缓存代际（跨节点时用 Redis AtomicLong） */
    public long currentGeneration() {
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            return redisson.getAtomicLong(REDIS_GEN_KEY).get();
        }
        return localGeneration.get();
    }

    /**
     * 写事务成功提交后：递增代际并清空 V2 值缓存；顺带 clear 遗留命名空间。
     * 回滚路径不得调用。
     */
    public void bumpGenerationAndEvict() {
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            RAtomicLong atomic = redisson.getAtomicLong(REDIS_GEN_KEY);
            atomic.incrementAndGet();
        } else {
            localGeneration.incrementAndGet();
        }
        evictValuesOnly();
        log.debug("[bumpGenerationAndEvict] gen={} v2+legacy cleared", currentGeneration());
    }

    /** 兼容旧调用名 */
    public void evictNow() {
        bumpGenerationAndEvict();
    }

    /**
     * 清空 V2（本版读写）与遗留命名空间（仅 clear，帮助旧实例 miss）。
     * 不改代际。
     */
    public void evictValuesOnly() {
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        // 滚动窗口：旧实例仍用遗留名；组织写后清旧名可促使其重新读库
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
    }

    /**
     * 若 V2 缓存命中且代际新鲜则返回 ids，否则 null（视为 miss）。
     */
    public Set<Long> getIfFresh(Long deptId) {
        Cache cache = valueCacheV2();
        if (cache == null || deptId == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(deptId);
        if (wrapper == null || wrapper.get() == null) {
            return null;
        }
        Object raw = wrapper.get();
        if (!(raw instanceof GenerationStampedSet stamped)) {
            // V2 内非法条目：丢弃（不应出现遗留 Set）
            cache.evict(deptId);
            return null;
        }
        long now = currentGeneration();
        if (stamped.generation() != now) {
            cache.evict(deptId);
            return null;
        }
        return stamped.ids() == null ? Collections.emptySet() : stamped.ids();
    }

    /**
     * 仅当 {@code loadGeneration} 仍等于当前代际时写入 <b>V2</b> 命名空间。
     *
     * @return true 已写入；false 代际已变，未写入
     */
    public boolean putIfGeneration(Long deptId, long loadGeneration, Set<Long> ids) {
        LongConsumer hook = afterLoadBeforePutHook;
        if (hook != null) {
            hook.accept(loadGeneration);
        }
        long now = currentGeneration();
        if (now != loadGeneration) {
            log.debug("[putIfGeneration] reject put deptId={} loadGen={} nowGen={}",
                    deptId, loadGeneration, now);
            return false;
        }
        Cache cache = valueCacheV2();
        if (cache == null || deptId == null) {
            return false;
        }
        Set<Long> safe = ids == null ? Collections.emptySet() : Set.copyOf(ids);
        cache.put(deptId, new GenerationStampedSet(loadGeneration, safe));
        if (currentGeneration() != loadGeneration) {
            cache.evict(deptId);
            return false;
        }
        return true;
    }

    /** 本版读写的缓存名（供测试断言 C1 命名空间） */
    public static String activeCacheName() {
        return RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2;
    }

    /** 遗留缓存名（旧实例 / 回滚包使用） */
    public static String legacyCacheName() {
        return RedisKeyConstants.DEPT_CHILDREN_ID_LIST;
    }

    private Cache valueCacheV2() {
        return getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
    }

    private void clearCache(String name) {
        Cache cache = getCache(name);
        if (cache != null) {
            cache.clear();
        }
    }

    private Cache getCache(String name) {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            return null;
        }
        return cacheManager.getCache(name);
    }

    /**
     * 缓存值：带代际戳的子部门 id 集合。仅允许出现在 V2 命名空间。
     */
    public record GenerationStampedSet(long generation, Set<Long> ids) implements Serializable {
        public GenerationStampedSet {
            ids = ids == null ? Set.of() : Set.copyOf(ids);
        }
    }

}
