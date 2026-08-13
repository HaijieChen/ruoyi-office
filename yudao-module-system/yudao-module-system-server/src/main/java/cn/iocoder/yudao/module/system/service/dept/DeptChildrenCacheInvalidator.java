package cn.iocoder.yudao.module.system.service.dept;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * 组织子树缓存协调器：代际 + cache-aside（G2-TAIL）+ 命名空间隔离（C1）
 * + 旧写失效信号（R2-FINAL-01）+ 禁止读路径覆盖 epoch（R2-FINAL-01-TAIL）。
 * <p>
 * <b>C1</b>：{@link GenerationStampedSet} 只写入 {@link RedisKeyConstants#DEPT_CHILDREN_ID_LIST_V2}。
 * <p>
 * <b>G2-TAIL</b>：写成功 {@link #bumpGenerationAndEvict()}；读路径 {@link #putIfGeneration} 拒绝过期 put。
 * <p>
 * <b>R2-FINAL-01</b>：legacy 中放置 {@link Long} epoch 标记；旧包 allEntries clear 抹掉标记；
 * 新包 {@link #getIfFresh} 检测后 bump+清 V2。
 * <p>
 * <b>R2-FINAL-01-TAIL</b>：普通读的 {@link #putIfGeneration} <strong>禁止</strong>
 * {@code restoreLegacyEpochMarker}。epoch 仅允许由
 * {@link #bumpGenerationAndEvict()} / {@link #onLegacyInvalidationSignal()} 在 clear 后写入<strong>当前 gen</strong>，
 * 不得用更早的 {@code loadGen} 覆盖「末次 epoch GET 与 restore 之间」的旧写 clear。
 * <p>
 * 部署：滚动窗口新旧写者可并存（新读可检测旧写失效且不会被读路径 restore 抹掉）。
 */
@Component
@Slf4j
public class DeptChildrenCacheInvalidator {

    private static final String REDIS_GEN_KEY = "system:dept:children:gen";

    /**
     * 遗留 cache 中的 epoch 标记键（String，非部门 Long id）。
     * 值类型 {@link Long}：旧 classpath 可反序列化。
     */
    public static final String LEGACY_EPOCH_MARKER_KEY = "__dept_children_v2_epoch__";

    @Resource
    private ObjectProvider<CacheManager> cacheManagerProvider;
    @Resource
    private ObjectProvider<RedissonClient> redissonClientProvider;

    private final AtomicLong localGeneration = new AtomicLong(0L);

    /** G2-TAIL：load 后、put 前 */
    @VisibleForTesting
    volatile LongConsumer afterLoadBeforePutHook;

    /**
     * R2-FINAL-01-TAIL：末次 epoch intact 检查通过之后、方法返回之前。
     * 用于注入「旧包 clear legacy」且证明不会 restore 覆盖。
     */
    @VisibleForTesting
    volatile Runnable afterLastEpochCheckBeforeReturnHook;

    public long currentGeneration() {
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            return redisson.getAtomicLong(REDIS_GEN_KEY).get();
        }
        return localGeneration.get();
    }

    /**
     * 写事务成功提交后：递增代际、清空 V2+遗留，并写回 legacy epoch=当前 gen。
     * （唯一允许的「写路径」restore 之一；gen 为 bump 后的新值，非过期 loadGen）
     */
    public void bumpGenerationAndEvict() {
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            redisson.getAtomicLong(REDIS_GEN_KEY).incrementAndGet();
        } else {
            localGeneration.incrementAndGet();
        }
        evictValuesOnly();
        writeLegacyEpochMarker(currentGeneration());
        log.debug("[bumpGenerationAndEvict] gen={} v2 cleared, legacy epoch written", currentGeneration());
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
            log.debug("[getIfFresh] legacy epoch broken → old-package write signal, invalidate V2");
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
     * 仅当 loadGeneration 仍等于当前 gen 且 epoch 完好时写入 V2。
     * <p>
     * <b>R2-FINAL-01-TAIL</b>：成功 put 后<strong>不</strong>调用 writeLegacyEpochMarker。
     * epoch 完好时无需写回；若旧写在末次 GET 之后 clear 了 epoch，禁止用 loadGen 覆盖该信号。
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
        // 末次校验：gen 或 epoch 已变则撤回 put
        if (currentGeneration() != loadGeneration || !isLegacyEpochSignalIntact()) {
            cache.evict(deptId);
            if (!isLegacyEpochSignalIntact()) {
                onLegacyInvalidationSignal();
            }
            return false;
        }
        // R2-FINAL-01-TAIL 测试钩子：插在「末次 epoch intact」之后；此处不得再 writeLegacyEpochMarker
        Runnable tailHook = afterLastEpochCheckBeforeReturnHook;
        if (tailHook != null) {
            tailHook.run();
        }
        // 若钩子期间旧写 clear 了 epoch：仍不得 restore；返回 true 时 V2 可能短暂存在，
        // 但后续 getIfFresh 会因 epoch 破损失效（见 tail 回归测）
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

    /**
     * 旧包 clear / 首次无标记：bump gen + 清 V2 + 写入 epoch=新 gen。
     * 此处写 epoch 用的是 bump 后的当前 gen，不是过期 loadGen。
     */
    private void onLegacyInvalidationSignal() {
        RedissonClient redisson = redissonClientProvider.getIfAvailable();
        if (redisson != null) {
            redisson.getAtomicLong(REDIS_GEN_KEY).incrementAndGet();
        } else {
            localGeneration.incrementAndGet();
        }
        clearCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        writeLegacyEpochMarker(currentGeneration());
    }

    /**
     * 仅允许从 bumpGenerationAndEvict / onLegacyInvalidationSignal 调用。
     * 禁止 putIfGeneration 成功路径调用（R2-FINAL-01-TAIL）。
     */
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
