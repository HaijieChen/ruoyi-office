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
    }

    @AfterEach
    void tearDown() {
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
