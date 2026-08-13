package cn.iocoder.yudao.module.system.service.dept;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * 组织子树缓存协调器：代际 + cache-aside（G2-TAIL）+ 命名空间隔离（C1）
 * + 旧写失效信号（R2-FINAL-01 / TAIL）+ <b>租户作用域一致（R2-FINAL-02）</b>。
 * <p>
 * <b>R2-FINAL-02</b>：generation、legacy epoch、V2 均按当前租户分区。
 * Redisson key：{@code system:dept:children:gen:{tenantId}}；本地 fallback：
 * {@code ConcurrentHashMap&lt;tenantId, AtomicLong&gt;}。
 * 与 {@code TenantRedisCacheManager} 对 cacheName 追加 {@code :tenantId} 的作用域对齐，
 * 避免租户 A 的 epoch miss 全局 bump 导致租户 B 代际 ping-pong。
 */
@Component
@Slf4j
public class DeptChildrenCacheInvalidator {

    /** Redisson 代际 key 前缀；完整 key = prefix + tenantId */
    public static final String REDIS_GEN_KEY_PREFIX = "system:dept:children:gen:";

    /**
     * 遗留 cache 中的 epoch 标记键（String，非部门 Long id）。
     * 值类型 {@link Long}：旧 classpath 可反序列化。
     * 实际存储位置随 {@link CacheManager} 租户后缀隔离。
     */
    public static final String LEGACY_EPOCH_MARKER_KEY = "__dept_children_v2_epoch__";

    @Resource
    private ObjectProvider<CacheManager> cacheManagerProvider;
    @Resource
    private ObjectProvider<RedissonClient> redissonClientProvider;

    /** 无 Redisson 时按租户的进程内代际 */
    private final ConcurrentHashMap<Long, AtomicLong> localGenerationByTenant = new ConcurrentHashMap<>();

    @VisibleForTesting
    volatile LongConsumer afterLoadBeforePutHook;

    @VisibleForTesting
    volatile Runnable afterLastEpochCheckBeforeReturnHook;

    /** 当前租户 id（与锁/TenantRedisCacheManager 一致：null → 0） */
    public long currentTenantId() {
        return ObjectUtil.defaultIfNull(TenantContextHolder.getTenantId(), 0L);
    }

    /** 当前租户的 generation Redis key（供测试断言） */
    public String redisGenerationKey() {
        return REDIS_GEN_KEY_PREFIX + currentTenantId();
    }

    public long currentGeneration() {
        Long tenantId = currentTenantId();
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            return redisson.getAtomicLong(REDIS_GEN_KEY_PREFIX + tenantId).get();
        }
        return localGenerationByTenant
                .computeIfAbsent(tenantId, id -> new AtomicLong(0L))
                .get();
    }

    /**
     * 写事务成功提交后：递增<strong>当前租户</strong>代际、清空该租户 V2+遗留，写 epoch。
     */
    public void bumpGenerationAndEvict() {
        bumpGenerationOnly();
        evictValuesOnly();
        writeLegacyEpochMarker(currentGeneration());
        log.debug("[bumpGenerationAndEvict] tenant={} gen={}", currentTenantId(), currentGeneration());
    }

    public void evictNow() {
        bumpGenerationAndEvict();
    }

    public void evictValuesOnly() {
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
    }

    public Set<Long> getIfFresh(Long deptId) {
        if (deptId == null) {
            return null;
        }
        if (!isLegacyEpochSignalIntact()) {
            log.debug("[getIfFresh] tenant={} legacy epoch broken → invalidate V2", currentTenantId());
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
     * 仅当 loadGeneration 仍等于<strong>当前租户</strong> gen 且 epoch 完好时写入 V2。
     * 成功 put 后不写 epoch（R2-FINAL-01-TAIL）。
     */
    public boolean putIfGeneration(Long deptId, long loadGeneration, Set<Long> ids) {
        LongConsumer loadHook = afterLoadBeforePutHook;
        if (loadHook != null) {
            loadHook.accept(loadGeneration);
        }
        long now = currentGeneration();
        if (now != loadGeneration) {
            return false;
        }
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
        Runnable tailHook = afterLastEpochCheckBeforeReturnHook;
        if (tailHook != null) {
            tailHook.run();
        }
        return true;
    }

    public boolean isLegacyEpochSignalIntact() {
        Cache legacy = getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        if (legacy == null) {
            return true;
        }
        Cache.ValueWrapper wrapper = legacy.get(LEGACY_EPOCH_MARKER_KEY);
        if (wrapper == null || wrapper.get() == null) {
            return false;
        }
        return toLong(wrapper.get()) == currentGeneration();
    }

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

    private void bumpGenerationOnly() {
        Long tenantId = currentTenantId();
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            redisson.getAtomicLong(REDIS_GEN_KEY_PREFIX + tenantId).incrementAndGet();
        } else {
            localGenerationByTenant
                    .computeIfAbsent(tenantId, id -> new AtomicLong(0L))
                    .incrementAndGet();
        }
    }

    private void onLegacyInvalidationSignal() {
        bumpGenerationOnly();
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        writeLegacyEpochMarker(currentGeneration());
    }

    private void writeLegacyEpochMarker(long gen) {
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
