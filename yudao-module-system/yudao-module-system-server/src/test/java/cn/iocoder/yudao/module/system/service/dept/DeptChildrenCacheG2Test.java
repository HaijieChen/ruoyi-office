package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G2：组织子树缓存仅在最外层事务 commit 成功后失效。
 */
@Import({DeptServiceImpl.class, DeptMutationLock.class, DeptChildrenCacheInvalidator.class,
        DeptChildrenCacheG2Test.CacheTestConfig.class})
public class DeptChildrenCacheG2Test extends BaseDbUnitTest {

    @Resource
    private DeptService deptService;
    @Resource
    private DeptMutationLock deptMutationLock;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private CacheManager cacheManager;

    public static class CacheTestConfig {
        @Bean
        @Primary
        public CacheManager deptChildrenCacheManager() {
            return new ConcurrentMapCacheManager(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        }
    }

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
        Cache cache = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void nestedMutationDoesNotEvictUntilOutermostCommit() {
        Long parentId = createRootCompany("G2父公司");
        Cache cache = cache();
        Set<Long> staleSnapshot = Collections.emptySet();
        cache.put(parentId, staleSnapshot);
        assertNotNull(cache.get(parentId));

        AtomicBoolean midStillPresent = new AtomicBoolean(false);
        AtomicReference<Object> midValue = new AtomicReference<>();

        deptMutationLock.execute(() -> {
            DeptSaveReqVO child = new DeptSaveReqVO();
            child.setName("G2子部门");
            child.setParentId(parentId);
            child.setSort(1);
            child.setStatus(CommonStatusEnum.ENABLE.getStatus());
            child.setOrgType("0");
            // 内层 join 同一事务：不得在内层返回时 evict
            deptService.createDept(child);

            Cache.ValueWrapper wrapper = cache.get(parentId);
            midStillPresent.set(wrapper != null);
            midValue.set(wrapper != null ? wrapper.get() : null);
            return null;
        });

        assertTrue(midStillPresent.get(),
                "G2：嵌套 create 返回时不得提前 evict（否则并发可读库旧快照回填）");
        assertEquals(staleSnapshot, midValue.get());

        // 外层 commit 成功后、unlock 前已 evict
        assertNull(cache.get(parentId), "成功提交后子树缓存必须失效，下次读从库重建");

        long children = deptMapper.selectList(new DeptListReqVO()).stream()
                .filter(d -> parentId.equals(d.getParentId()) && "G2子部门".equals(d.getName()))
                .count();
        assertEquals(1L, children);
    }

    @Test
    void rollbackDoesNotEvictCache() {
        Long parentId = createRootCompany("G2回滚父");
        Cache cache = cache();
        Set<Long> pre = new HashSet<>();
        pre.add(999L);
        cache.put(parentId, pre);

        assertThrows(IllegalStateException.class, () -> deptMutationLock.execute(() -> {
            DeptSaveReqVO child = new DeptSaveReqVO();
            child.setName("将回滚的子");
            child.setParentId(parentId);
            child.setSort(1);
            child.setStatus(CommonStatusEnum.ENABLE.getStatus());
            child.setOrgType("0");
            deptService.createDept(child);
            throw new IllegalStateException("force rollback");
        }));

        Cache.ValueWrapper after = cache.get(parentId);
        assertNotNull(after, "回滚路径不得 evict（避免错误缓存终态窗口）");
        assertEquals(pre, after.get());
        assertTrue(deptMapper.selectList(new DeptListReqVO()).stream()
                .noneMatch(d -> "将回滚的子".equals(d.getName())));
    }

    @Test
    void successfulCreateClearsCacheAndReloadSeesNewChild() {
        Long parentId = createRootCompany("G2可见父");
        Cache cache = cache();
        cache.put(parentId, Collections.emptySet());

        DeptSaveReqVO child = new DeptSaveReqVO();
        child.setName("G2可见子");
        child.setParentId(parentId);
        child.setSort(1);
        child.setStatus(CommonStatusEnum.ENABLE.getStatus());
        child.setOrgType("0");
        Long childId = deptService.createDept(child);

        assertNull(cache.get(parentId), "提交后缓存失效");

        Set<Long> reloaded = deptService.getChildDeptList(parentId).stream()
                .map(DeptDO::getId)
                .collect(Collectors.toSet());
        assertTrue(reloaded.contains(childId), "提交后库可见新子节点");
    }

    @Test
    void midTxConcurrentStyleRefillWouldNotStickAfterCommit() {
        // 复现 G2 窗口的对抗形态：提交前若被错误 clear，并发会用「库旧快照」回填；
        // 修复后提交前不清，提交后 clear，回填无法在 commit 后残留。
        Long parentId = createRootCompany("G2对抗父");
        Cache cache = cache();
        cache.put(parentId, Collections.emptySet());

        deptMutationLock.execute(() -> {
            DeptSaveReqVO child = new DeptSaveReqVO();
            child.setName("G2对抗子");
            child.setParentId(parentId);
            child.setSort(1);
            child.setStatus(CommonStatusEnum.ENABLE.getStatus());
            child.setOrgType("0");
            deptService.createDept(child);

            // 模拟「提交前」并发读：应仍看到事务前缓存条目（未被 clear），
            // 而不是 clear 后的 miss→按未提交库写入 empty
            assertNotNull(cache.get(parentId), "提交前缓存应仍在（未被错误 clear）");
            // 若有人误 clear，此处可再 put 旧快照模拟回填：
            // cache.put(parentId, Collections.emptySet());
            return null;
        });

        assertNull(cache.get(parentId), "commit 后必须 clear，旧回填无法残留");
        assertEquals(1, deptService.getChildDeptList(parentId).size());
    }

    private Long createRootCompany(String name) {
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName(name);
        req.setParentId(DeptDO.PARENT_ID_ROOT);
        req.setSort(0);
        req.setStatus(CommonStatusEnum.ENABLE.getStatus());
        req.setOrgType("1");
        req.setFunctionalCurrency("CNY");
        return deptService.createDept(req);
    }

    private Cache cache() {
        Cache cache = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        assertNotNull(cache);
        return cache;
    }

}
