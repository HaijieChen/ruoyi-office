package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2-FINAL-01：旧写→新读。模拟旧包仅 {@code @CacheEvict(legacy, allEntries)}，
 * 新包必须拒绝陈旧 V2 命中并看到库中新子树。
 */
@Import({DeptServiceImpl.class, DeptMutationLock.class, DeptChildrenCacheInvalidator.class,
        DeptChildrenCacheReverseCompatTest.CacheTestConfig.class})
public class DeptChildrenCacheReverseCompatTest extends BaseDbUnitTest {

    @Resource
    private DeptService deptService;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private DeptChildrenCacheInvalidator coordinator;
    @Resource
    private CacheManager cacheManager;

    public static class CacheTestConfig {
        @Bean
        @Primary
        public CacheManager dualNamespaceCacheManager() {
            return new ConcurrentMapCacheManager(
                    RedisKeyConstants.DEPT_CHILDREN_ID_LIST,
                    RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        }
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        coordinator.afterLoadBeforePutHook = null;
        coordinator.afterLastEpochCheckBeforeReturnHook = null;
    }

    @AfterEach
    void tearDown() {
        coordinator.afterLoadBeforePutHook = null;
        coordinator.afterLastEpochCheckBeforeReturnHook = null;
        TenantContextHolder.clear();
        Cache a = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        Cache b = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        if (a != null) {
            a.clear();
        }
        if (b != null) {
            b.clear();
        }
    }

    @Test
    void oldPackageLegacyEvictOnly_newPackageMustMissStaleV2() {
        Long parentId = createRoot("R2F01父");
        // 新包写子 + 填充 V2
        Long childOld = createDeptUnder("R2F01旧子", parentId);
        Set<Long> warm = deptService.getChildDeptIdListFromCache(parentId);
        assertTrue(warm.contains(childOld));
        long genBefore = coordinator.currentGeneration();
        assertTrue(coordinator.isLegacyEpochSignalIntact(), "新包写后应有 epoch 标记");

        // 直接断言 V2 有命中
        assertNotNull(coordinator.getIfFresh(parentId));

        // —— 模拟旧包组织写：库中新增子节点，但只 @CacheEvict 遗留名 ——
        DeptDO injected = new DeptDO();
        injected.setName("R2F01旧包新增子");
        injected.setParentId(parentId);
        injected.setSort(2);
        injected.setStatus(CommonStatusEnum.ENABLE.getStatus());
        injected.setOrgType("0");
        deptMapper.insert(injected);
        Long childNew = injected.getId();
        assertNotNull(childNew);

        // 旧包行为：仅 clear legacy（含 epoch），不 bump gen、不清 V2
        coordinator.simulateOldPackageCacheEvictAllEntries();
        assertFalse(coordinator.isLegacyEpochSignalIntact(), "旧包 clear 后 epoch 应缺失");

        // 若未修复，此处 gen 不变且 V2 仍含旧子树 → getIfFresh 会错误返回仅 [childOld]
        // 修复后：检测到 epoch 破损 → bump + 清 V2 → miss → 读库含新子
        Set<Long> afterOldWrite = deptService.getChildDeptIdListFromCache(parentId);
        assertTrue(afterOldWrite.contains(childOld));
        assertTrue(afterOldWrite.contains(childNew),
                "R2-FINAL-01：旧包写后新包不得命中陈旧 V2，须 miss 重载含新子");
        assertTrue(coordinator.currentGeneration() > genBefore, "检测旧写后应 bump 代际");
        assertTrue(coordinator.isLegacyEpochSignalIntact(), "重载后应恢复 epoch 标记");
    }

    @Test
    void getIfFreshRejectsStaleV2WhenLegacyClearedWithoutBump() {
        Long parentId = createRoot("R2F01直接父");
        createDeptUnder("R2F01子A", parentId);
        deptService.getChildDeptIdListFromCache(parentId);
        long gen = coordinator.currentGeneration();

        // 手工保持 V2 与 gen，仅清 legacy（旧写）
        coordinator.simulateOldPackageCacheEvictAllEntries();
        // V2 条目仍在（模拟旧包不知 V2）
        Cache v2 = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        assertNotNull(v2.get(parentId));
        assertEquals(gen, coordinator.currentGeneration());

        // getIfFresh 必须因 epoch 破损返回 null，而不是返回 V2 旧集合
        assertNull(coordinator.getIfFresh(parentId),
                "legacy epoch 破损时不得返回陈旧 V2 命中");
    }

    /**
     * R2-FINAL-01-TAIL：
     * <pre>
     * 新读 putIfGeneration：末次 epoch GET intact
     * 旧写：DB + legacy allEntries clear（删 epoch）
     * 新读：返回（且不得 restore epoch=loadGen）
     * 后读：不得有效命中陈旧 V2
     * </pre>
     */
    @Test
    void putSuccessMustNotRestoreEpochOverOldWriteClear() throws Exception {
        Long parentId = createRoot("R2F01TAIL父");
        // 先 warm 一次，确保有 epoch；再 clear V2 触发 miss put 路径
        deptService.getChildDeptIdListFromCache(parentId);
        long genWarm = coordinator.currentGeneration();
        assertTrue(coordinator.isLegacyEpochSignalIntact());
        Cache v2 = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        assertNotNull(v2);
        v2.clear(); // 强制 miss → putIfGeneration

        CountDownLatch readerAtTail = new CountDownLatch(1);
        CountDownLatch oldWriteDone = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        coordinator.afterLastEpochCheckBeforeReturnHook = () -> {
            try {
                readerAtTail.countDown();
                assertTrue(oldWriteDone.await(15, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> readerFut = pool.submit(() -> {
            TenantContextHolder.setTenantId(1L);
            try {
                // miss → load 空子树（旧写尚未插入）→ put V2；末次 epoch check 后钩子暂停
                Set<Long> first = deptService.getChildDeptIdListFromCache(parentId);
                assertNotNull(first);
                // 返回后：若错误 restore 了 epoch，陈旧 empty V2 会再次被 getIfFresh 命中
            } catch (Throwable t) {
                error.set(t);
            } finally {
                TenantContextHolder.clear();
            }
        });

        Future<?> oldWriterFut = pool.submit(() -> {
            TenantContextHolder.setTenantId(1L);
            try {
                assertTrue(readerAtTail.await(15, TimeUnit.SECONDS));
                // 旧包：库变更 + 仅 clear legacy（抹 epoch）
                DeptDO injected = new DeptDO();
                injected.setName("R2F01TAIL旧包子");
                injected.setParentId(parentId);
                injected.setSort(9);
                injected.setStatus(CommonStatusEnum.ENABLE.getStatus());
                injected.setOrgType("0");
                deptMapper.insert(injected);
                coordinator.simulateOldPackageCacheEvictAllEntries();
                assertFalse(coordinator.isLegacyEpochSignalIntact());
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            } finally {
                oldWriteDone.countDown();
                TenantContextHolder.clear();
            }
        });

        readerFut.get(30, TimeUnit.SECONDS);
        oldWriterFut.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        coordinator.afterLastEpochCheckBeforeReturnHook = null;

        if (error.get() != null) {
            fail(error.get());
        }

        // 关键 restore 后 epoch 仍破损 → 后读必须 miss 重载，不得命中 put 进去的空 V2
        assertFalse(coordinator.isLegacyEpochSignalIntact()
                        && coordinator.currentGeneration() == genWarm
                        && coordinator.getIfFresh(parentId) != null
                        && coordinator.getIfFresh(parentId).isEmpty(),
                "不得出现：restore 掩盖旧写 + 陈旧 empty V2 有效命中");

        Set<Long> after = deptService.getChildDeptIdListFromCache(parentId);
        assertTrue(after.stream().anyMatch(id -> {
            DeptDO d = deptMapper.selectById(id);
            return d != null && "R2F01TAIL旧包子".equals(d.getName());
        }), "后续读必须看见旧写插入的子节点");
        assertTrue(coordinator.currentGeneration() > genWarm
                        || !after.isEmpty(),
                "应 bump 或至少从库读到新子");
    }

    private Long createRoot(String name) {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName(name);
        req.setParentId(DeptDO.PARENT_ID_ROOT);
        req.setSort(0);
        req.setStatus(CommonStatusEnum.ENABLE.getStatus());
        req.setOrgType("1");
        req.setFunctionalCurrency("CNY");
        return deptService.createDept(req);
    }

    private Long createDeptUnder(String name, Long parentId) {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName(name);
        req.setParentId(parentId);
        req.setSort(1);
        req.setStatus(CommonStatusEnum.ENABLE.getStatus());
        req.setOrgType("0");
        return deptService.createDept(req);
    }

}
