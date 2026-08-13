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
 * 组织子树缓存协调器：代际 + cache-aside（G2-TAIL）+ 命名空间隔离（C1）+ 旧写失效信号（R2-FINAL-01）。
 * <p>
 * <b>C1</b>：{@link GenerationStampedSet} 只写入 {@link RedisKeyConstants#DEPT_CHILDREN_ID_LIST_V2}。
 * <p>
 * <b>G2-TAIL</b>：写成功 {@link #bumpGenerationAndEvict()}；读路径 {@link #putIfGeneration} 拒绝过期 put。
 * <p>
 * <b>R2-FINAL-01（旧写→新读）</b>：新包在遗留命名空间放置 epoch 标记（{@link #LEGACY_EPOCH_MARKER_KEY}，值为
 * {@link Long}，旧 classpath 可反序列化）。旧包组织写仅 {@code @CacheEvict(dept_children_ids, allEntries)}，
 * 会清掉该标记且不碰 V2/gen。新包 {@link #getIfFresh} 发现标记缺失或与当前 gen 不一致时，视为旧写失效信号：
 * bump gen + clear V2，拒绝陈旧 V2 命中并强制 miss 重载。
 * <p>
 * 部署：允许滚动窗口内新旧<strong>写者</strong>并存——新读路径可检测旧写失效信号；
 * 非「仅靠类型隔离」宣称一致性。
 */
@Component
@Slf4j
public class DeptChildrenCacheInvalidator {

    private static final String REDIS_GEN_KEY = "system:dept:children:gen";

    /**
     * 遗留 cache 中的 epoch 标记键（String，非部门 Long id）。
     * 旧包只按部门 id 做 {@code @Cacheable}，不会读取此键；但 allEntries clear 会删掉它。
     * 值类型为 {@link Long}，旧包即便误读也可 JSON 反序列化。
     */
    public static final String LEGACY_EPOCH_MARKER_KEY = "__dept_children_v2_epoch__";

    @Resource
    private ObjectProvider<CacheManager> cacheManagerProvider;
    @Resource
    private ObjectProvider<RedissonClient> redissonClientProvider;

    private final AtomicLong localGeneration = new AtomicLong(0L);

    @VisibleForTesting
    volatile LongConsumer afterLoadBeforePutHook;

    public long currentGeneration() {
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            return redisson.getAtomicLong(REDIS_GEN_KEY).get();
        }
        return localGeneration.get();
    }

    /**
     * 写事务成功提交后：递增代际、清空 V2+遗留值缓存，并写回 legacy epoch 标记。
     */
    public void bumpGenerationAndEvict() {
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            redisson.getAtomicLong(REDIS_GEN_KEY).incrementAndGet();
        } else {
            localGeneration.incrementAndGet();
        }
        evictValuesOnly();
        restoreLegacyEpochMarker(currentGeneration());
        log.debug("[bumpGenerationAndEvict] gen={} v2 cleared, legacy epoch restored", currentGeneration());
    }

    public void evictNow() {
        bumpGenerationAndEvict();
    }

    /**
     * 清空 V2 与遗留命名空间（不改 gen）。clear 后不自动写 epoch（由调用方 restore）。
     */
    public void evictValuesOnly() {
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
    }

    /**
     * 若 V2 命中且（1）legacy epoch 信号完好（2）条目 gen 新鲜，则返回 ids；否则 null。
     * <p>
     * 当检测到旧包写导致的 legacy clear 时，会 bump gen 并清空 V2，强制后续 miss。
     */
    public Set<Long> getIfFresh(Long deptId) {
        if (deptId == null) {
            return null;
        }
        // R2-FINAL-01：先检测旧写失效信号
        if (!isLegacyEpochSignalIntact()) {
            log.debug("[getIfFresh] legacy epoch broken → treat as old-package write, invalidate V2");
            onLegacyInvalidationSignal();
            return null;
        }
        Cache cache = valueCacheV2();
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(deptId);
        if (wrapper == null || wrapper.get() == null) {
            return null;
        }
        Object raw = wrapper.get();
        if (!(raw instanceof GenerationStampedSet stamped)) {
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
     * 仅当 loadGeneration 仍等于当前 gen 时写入 V2；成功后刷新 legacy epoch。
     */
    public boolean putIfGeneration(Long deptId, long loadGeneration, Set<Long> ids) {
        LongConsumer hook = afterLoadBeforePutHook;
        if (hook != null) {
            hook.accept(loadGeneration);
        }
        long now = currentGeneration();
        if (now != loadGeneration) {
            return false;
        }
        // put 前再确认 epoch：若旧写刚 clear legacy，勿写入陈旧 load
        if (!isLegacyEpochSignalIntact()) {
            onLegacyInvalidationSignal();
            return false;
        }
        Cache cache = valueCacheV2();
        if (cache == null || deptId == null) {
            return false;
        }
        Set<Long> safe = ids == null ? Collections.emptySet() : Set.copyOf(ids);
        cache.put(deptId, new GenerationStampedSet(loadGeneration, safe));
        if (currentGeneration() != loadGeneration || !isLegacyEpochSignalIntact()) {
            cache.evict(deptId);
            if (!isLegacyEpochSignalIntact()) {
                onLegacyInvalidationSignal();
            }
            return false;
        }
        restoreLegacyEpochMarker(loadGeneration);
        return true;
    }

    /**
     * 遗留 epoch 是否与当前 gen 一致（标记存在且值相等）。
     * 无 CacheManager 时返回 true（无法检测，降级为仅代际逻辑）。
     */
    public boolean isLegacyEpochSignalIntact() {
        Cache legacy = getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        if (legacy == null) {
            return true;
        }
        Cache.ValueWrapper wrapper = legacy.get(LEGACY_EPOCH_MARKER_KEY);
        if (wrapper == null || wrapper.get() == null) {
            return false;
        }
        long marker = toLong(wrapper.get());
        return marker == currentGeneration();
    }

    /**
     * 模拟旧包组织写：仅 clear 遗留命名空间（含 epoch），不 bump gen、不清 V2。
     * 仅测试使用。
     */
    @VisibleForTesting
    public void simulateOldPackageCacheEvictAllEntries() {
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
    }

    public static String activeCacheName() {
        return RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2;
    }

    public static String legacyCacheName() {
        return RedisKeyConstants.DEPT_CHILDREN_ID_LIST;
    }

    /**
     * 检测到旧包 allEntries clear（或首次无标记）后：bump gen + 清 V2 + 写回 epoch。
     */
    private void onLegacyInvalidationSignal() {
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            redisson.getAtomicLong(REDIS_GEN_KEY).incrementAndGet();
        } else {
            localGeneration.incrementAndGet();
        }
        // 只清 V2 值；legacy 已被旧包 clear，此处写回 epoch
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        restoreLegacyEpochMarker(currentGeneration());
    }

    private void restoreLegacyEpochMarker(long gen) {
        Cache legacy = getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        if (legacy == null) {
            return;
        }
        legacy.put(LEGACY_EPOCH_MARKER_KEY, gen);
    }

    private static long toLong(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(raw));
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

    public record GenerationStampedSet(long generation, Set<Long> ids) implements Serializable {
        public GenerationStampedSet {
            ids = ids == null ? Set.of() : Set.copyOf(ids);
        }
    }

}
