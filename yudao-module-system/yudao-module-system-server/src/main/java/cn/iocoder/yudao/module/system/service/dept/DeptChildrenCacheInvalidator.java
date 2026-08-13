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
 * 组织子树缓存协调器：代际（generation）+ cache-aside，闭合 G2-TAIL。
 * <p>
 * 窗口：
 * <pre>
 * R: miss → 读旧库 → [暂停]
 * W: commit + clear
 * R: put 旧快照  // 无代际时会污染缓存
 * </pre>
 * 修复：写成功路径 {@link #bumpGenerationAndEvict()} 递增代际并 clear；
 * 读路径 {@link #getIfFresh}/{@link #putIfGeneration}：仅当加载时的 gen 仍等于当前 gen 才 put；
 * 命中时 gen 不一致视为 miss。即便尾随 put 写入旧 gen 条目，后续 hit 也会拒绝。
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
     * 用于 latch 卡点构造 G2-TAIL 竞态。
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
     * 写事务成功提交后：递增代际并清空值缓存。
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
        log.debug("[bumpGenerationAndEvict] gen={} cache cleared", currentGeneration());
    }

    /**
     * 兼容旧调用名：等同 {@link #bumpGenerationAndEvict()}。
     */
    public void evictNow() {
        bumpGenerationAndEvict();
    }

    /** 仅 clear 值缓存，不改代际（测试辅助） */
    public void evictValuesOnly() {
        Cache cache = valueCache();
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * 若缓存命中且代际新鲜则返回 ids，否则 null（视为 miss）。
     */
    @SuppressWarnings("unchecked")
    public Set<Long> getIfFresh(Long deptId) {
        Cache cache = valueCache();
        if (cache == null || deptId == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(deptId);
        if (wrapper == null || wrapper.get() == null) {
            return null;
        }
        Object raw = wrapper.get();
        if (!(raw instanceof GenerationStampedSet stamped)) {
            // 非法/旧格式条目：丢弃
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
     * 仅当 {@code loadGeneration} 仍等于当前代际时写入；否则拒绝（闭合尾随 put）。
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
        Cache cache = valueCache();
        if (cache == null || deptId == null) {
            return false;
        }
        Set<Long> safe = ids == null ? Collections.emptySet() : Set.copyOf(ids);
        cache.put(deptId, new GenerationStampedSet(loadGeneration, safe));
        // 写入后再校验一次：若期间代际已变，删掉自己的 put，避免极短窗口残留
        if (currentGeneration() != loadGeneration) {
            cache.evict(deptId);
            return false;
        }
        return true;
    }

    private Cache valueCache() {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager == null) {
            return null;
        }
        return cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
    }

    /**
     * 缓存值：带代际戳的子部门 id 集合。
     */
    public record GenerationStampedSet(long generation, Set<Long> ids) implements Serializable {
        public GenerationStampedSet {
            ids = ids == null ? Set.of() : Set.copyOf(ids);
        }
    }

}
