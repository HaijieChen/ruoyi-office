package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R2-FINAL-02：generation / epoch / V2 租户作用域一致，禁止跨租户代际 ping-pong。
 */
@Import({DeptServiceImpl.class, DeptMutationLock.class, DeptChildrenCacheInvalidator.class,
        DeptChildrenCacheMultiTenantTest.CacheTestConfig.class})
public class DeptChildrenCacheMultiTenantTest extends BaseDbUnitTest {

    @Resource
    private DeptService deptService;
    @Resource
    private DeptChildrenCacheInvalidator coordinator;
    @Resource
    private CacheManager cacheManager;

    public static class CacheTestConfig {
        @Bean
        @Primary
        public CacheManager tenantPartitionedCacheManager() {
            return new TenantPartitionedConcurrentMapCacheManager(
                    RedisKeyConstants.DEPT_CHILDREN_ID_LIST,
                    RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        }
    }

    @AfterEach
    void clear() {
        coordinator.afterLoadBeforePutHook = null;
        coordinator.afterLastEpochCheckBeforeReturnHook = null;
        for (long t : new long[]{1L, 2L}) {
            TenantContextHolder.setTenantId(t);
            var leg = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
            var v2 = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
            if (leg != null) {
                leg.clear();
            }
            if (v2 != null) {
                v2.clear();
            }
        }
        TenantContextHolder.clear();
    }

    @Test
    void generationKeyIsTenantScoped() {
        TenantContextHolder.setTenantId(1L);
        assertEquals("system:dept:children:gen:1", coordinator.redisGenerationKey());
        TenantContextHolder.setTenantId(2L);
        assertEquals("system:dept:children:gen:2", coordinator.redisGenerationKey());
        assertNotEquals(
                coordinator.redisGenerationKey(),
                "system:dept:children:gen:1");
    }

    @Test
    void alternateTenantReadsDoNotCrossBumpGeneration() {
        // Tenant A warm
        TenantContextHolder.setTenantId(1L);
        Long parentA = createRoot("T1父");
        createDeptUnder("T1子", parentA);
        Set<Long> warmA = deptService.getChildDeptIdListFromCache(parentA);
        long genA1 = coordinator.currentGeneration();
        assertFalse(warmA.isEmpty());
        assertTrue(coordinator.isLegacyEpochSignalIntact());

        // Tenant B first read：epoch 缺 → 仅 bump B，不得动 A
        TenantContextHolder.setTenantId(2L);
        Long parentB = createRoot("T2父");
        long genBBeforeWarm = coordinator.currentGeneration();
        Set<Long> warmB = deptService.getChildDeptIdListFromCache(parentB);
        long genBAfterWarm = coordinator.currentGeneration();
        assertNotNull(warmB);
        // createRoot 会 bump；warm 后 gen 稳定
        assertTrue(genBAfterWarm >= genBBeforeWarm);

        // 回到 A：应仍命中，gen 不变（无跨租户 ping-pong）
        TenantContextHolder.setTenantId(1L);
        long genA2 = coordinator.currentGeneration();
        assertEquals(genA1, genA2, "租户 B 的读/写不得 bump 租户 A 代际");
        Set<Long> hitA = deptService.getChildDeptIdListFromCache(parentA);
        assertEquals(warmA, hitA, "租户 A 缓存命中应保持");
        assertEquals(genA2, coordinator.currentGeneration(), "A 再读不得额外 bump");

        // 再切 B 读：B gen 不因 A 读变化
        TenantContextHolder.setTenantId(2L);
        long genB2 = coordinator.currentGeneration();
        deptService.getChildDeptIdListFromCache(parentB);
        assertEquals(genB2, coordinator.currentGeneration(), "租户 B 稳定命中不得 ping-pong");
    }

    @Test
    void tenantAWriteOrOldClearDoesNotPingPongTenantB() {
        TenantContextHolder.setTenantId(1L);
        Long parentA = createRoot("T1写父");
        createDeptUnder("T1写子", parentA);
        deptService.getChildDeptIdListFromCache(parentA);
        long genA = coordinator.currentGeneration();

        TenantContextHolder.setTenantId(2L);
        Long parentB = createRoot("T2稳父");
        createDeptUnder("T2稳子", parentB);
        Set<Long> warmB = deptService.getChildDeptIdListFromCache(parentB);
        long genB = coordinator.currentGeneration();

        // A 新写 bump
        TenantContextHolder.setTenantId(1L);
        createDeptUnder("T1再写子", parentA);
        assertTrue(coordinator.currentGeneration() > genA);

        // B 不受影响
        TenantContextHolder.setTenantId(2L);
        assertEquals(genB, coordinator.currentGeneration());
        assertEquals(warmB, deptService.getChildDeptIdListFromCache(parentB));

        // A 模拟旧包 clear legacy
        TenantContextHolder.setTenantId(1L);
        long genA2 = coordinator.currentGeneration();
        coordinator.simulateOldPackageCacheEvictAllEntries();
        deptService.getChildDeptIdListFromCache(parentA); // 触发 epoch 修复 + 可能 bump
        assertTrue(coordinator.currentGeneration() >= genA2);

        TenantContextHolder.setTenantId(2L);
        assertEquals(genB, coordinator.currentGeneration(), "A 旧写 clear 不得导致 B 代际变化");
        assertEquals(warmB.size(), deptService.getChildDeptIdListFromCache(parentB).size());
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
