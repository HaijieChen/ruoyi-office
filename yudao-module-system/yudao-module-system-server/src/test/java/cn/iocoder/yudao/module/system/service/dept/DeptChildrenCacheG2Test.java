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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G2 + G2-TAIL：子树缓存 mid-tx 不 evict；提交后 bump 代际；尾随旧 put 不得成为有效命中。
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
    @Resource
    private DeptChildrenCacheInvalidator cacheCoordinator;

    public static class CacheTestConfig {
        @Bean
        @Primary
        public CacheManager deptChildrenCacheManager() {
            // V2 为主；遗留名一并注册供 clear 双清
            return new ConcurrentMapCacheManager(
                    RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2,
                    RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        }
    }

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(1L);
        cacheCoordinator.afterLoadBeforePutHook = null;
    }

    @AfterEach
    void clearTenant() {
        cacheCoordinator.afterLoadBeforePutHook = null;
        TenantContextHolder.clear();
        Cache cache = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        if (cache != null) {
            cache.clear();
        }
        Cache legacy = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        if (legacy != null) {
            legacy.clear();
        }
    }

    @Test
    void nestedMutationDoesNotEvictUntilOutermostCommit() {
        Long parentId = createRootCompany("G2父公司");
        Cache cache = cache();
        long gen = cacheCoordinator.currentGeneration();
        var stamped = new DeptChildrenCacheInvalidator.GenerationStampedSet(gen, Collections.emptySet());
        cache.put(parentId, stamped);
        assertNotNull(cache.get(parentId));

        AtomicBoolean midStillPresent = new AtomicBoolean(false);

        deptMutationLock.execute(() -> {
            DeptSaveReqVO child = new DeptSaveReqVO();
            child.setName("G2子部门");
            child.setParentId(parentId);
            child.setSort(1);
            child.setStatus(CommonStatusEnum.ENABLE.getStatus());
            child.setOrgType("0");
            deptService.createDept(child);

            midStillPresent.set(cache.get(parentId) != null);
            return null;
        });

        assertTrue(midStillPresent.get(), "G2：嵌套 create 返回时不得提前 clear");
        assertNull(cache.get(parentId), "成功提交后值缓存须 clear");
        assertTrue(cacheCoordinator.currentGeneration() > gen, "成功提交后须 bump 代际");

        long children = deptMapper.selectList(new DeptListReqVO()).stream()
                .filter(d -> parentId.equals(d.getParentId()) && "G2子部门".equals(d.getName()))
                .count();
        assertEquals(1L, children);
    }

    @Test
    void rollbackDoesNotBumpOrEvict() {
        Long parentId = createRootCompany("G2回滚父");
        long genBefore = cacheCoordinator.currentGeneration();
        Cache cache = cache();
        var pre = new DeptChildrenCacheInvalidator.GenerationStampedSet(genBefore, Set.of(999L));
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

        assertEquals(genBefore, cacheCoordinator.currentGeneration(), "回滚不得 bump 代际");
        Cache.ValueWrapper after = cache.get(parentId);
        assertNotNull(after, "回滚不得 clear 值缓存");
        assertEquals(pre, after.get());
        assertTrue(deptMapper.selectList(new DeptListReqVO()).stream()
                .noneMatch(d -> "将回滚的子".equals(d.getName())));
    }

    @Test
    void successfulCreateClearsCacheAndCacheReadSeesNewChild() {
        Long parentId = createRootCompany("G2可见父");
        Cache cache = cache();
        long gen = cacheCoordinator.currentGeneration();
        cache.put(parentId, new DeptChildrenCacheInvalidator.GenerationStampedSet(gen, Collections.emptySet()));

        DeptSaveReqVO child = new DeptSaveReqVO();
        child.setName("G2可见子");
        child.setParentId(parentId);
        child.setSort(1);
        child.setStatus(CommonStatusEnum.ENABLE.getStatus());
        child.setOrgType("0");
        Long childId = deptService.createDept(child);

        assertNull(cache.get(parentId), "提交后值缓存 clear");
        // 走真实缓存读路径（非 getChildDeptList）
        Set<Long> fromCache = deptService.getChildDeptIdListFromCache(parentId);
        assertTrue(fromCache.contains(childId), "提交后缓存重建须含新子");
    }

    /**
     * G2-TAIL 确定性并发：
     * <pre>
     * R: miss → 读旧库 → [latch: 已 load 待 put]
     * W: commit + bump+clear
     * R: putIfGeneration 拒绝（或 put 旧 gen 后 hit 拒绝）
     * 后续 hit 不得返回旧空集
     * </pre>
     */
    @Test
    void trailingStalePutCannotBecomeCacheHit() throws Exception {
        Long parentId = createRootCompany("G2TAIL父");
        // 确保 miss：clear + 不预置
        cache().clear();
        long genAtStart = cacheCoordinator.currentGeneration();

        CountDownLatch readerLoaded = new CountDownLatch(1);
        CountDownLatch writerDone = new CountDownLatch(1);
        AtomicBoolean putRejected = new AtomicBoolean(false);
        AtomicReference<Throwable> readerError = new AtomicReference<>();

        cacheCoordinator.afterLoadBeforePutHook = loadGen -> {
            try {
                readerLoaded.countDown();
                assertTrue(writerDone.await(15, TimeUnit.SECONDS), "writer must finish before reader put");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> readerFut = pool.submit(() -> {
            TenantContextHolder.setTenantId(1L);
            try {
                // 此时库中尚无子（writer 未 commit）→ 旧快照 empty；put 时 writer 已 bump → 拒绝
                Set<Long> first = deptService.getChildDeptIdListFromCache(parentId);
                assertNotNull(first);
                // 尾随 put 被拒后：不得存在「新鲜命中的空集」
                putRejected.set(cacheCoordinator.getIfFresh(parentId) == null);
                // 再次读取：须重建并看见 writer 提交的子
                Set<Long> second = deptService.getChildDeptIdListFromCache(parentId);
                assertFalse(second.isEmpty(), "修复后后续读不得命中旧空快照");
            } catch (Throwable t) {
                readerError.set(t);
            } finally {
                TenantContextHolder.clear();
            }
        });

        Future<?> writerFut = pool.submit(() -> {
            TenantContextHolder.setTenantId(1L);
            try {
                assertTrue(readerLoaded.await(15, TimeUnit.SECONDS), "reader must reach load barrier");
                DeptSaveReqVO child = new DeptSaveReqVO();
                child.setName("G2TAIL子");
                child.setParentId(parentId);
                child.setSort(1);
                child.setStatus(CommonStatusEnum.ENABLE.getStatus());
                child.setOrgType("0");
                deptService.createDept(child);
            } catch (Throwable t) {
                readerError.compareAndSet(null, t);
            } finally {
                writerDone.countDown();
                TenantContextHolder.clear();
            }
        });

        readerFut.get(30, TimeUnit.SECONDS);
        writerFut.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        cacheCoordinator.afterLoadBeforePutHook = null;

        if (readerError.get() != null) {
            fail(readerError.get());
        }
        assertTrue(putRejected.get(), "尾随 put 须被代际拒绝（getIfFresh 无旧 empty 命中）");
        assertTrue(cacheCoordinator.currentGeneration() > genAtStart, "writer 须 bump 代际");

        // 最终缓存命中必须含新子，不得是 gen 过期的 empty
        Set<Long> finalHit = deptService.getChildDeptIdListFromCache(parentId);
        assertEquals(1, finalHit.size());
        assertTrue(deptMapper.selectList(new DeptListReqVO()).stream()
                .anyMatch(d -> "G2TAIL子".equals(d.getName())));

        // 显式：即便有人强行 put 旧 gen empty，getIfFresh 也拒绝
        cache().put(parentId, new DeptChildrenCacheInvalidator.GenerationStampedSet(
                genAtStart, Collections.emptySet()));
        assertNull(cacheCoordinator.getIfFresh(parentId), "旧代际条目不得作为新鲜命中");
    }

    @Test
    void midTxDoesNotBumpGeneration() {
        Long parentId = createRootCompany("G2中途父");
        long gen = cacheCoordinator.currentGeneration();
        cache().put(parentId, new DeptChildrenCacheInvalidator.GenerationStampedSet(gen, Set.of()));

        deptMutationLock.execute(() -> {
            DeptSaveReqVO child = new DeptSaveReqVO();
            child.setName("G2中途子");
            child.setParentId(parentId);
            child.setSort(1);
            child.setStatus(CommonStatusEnum.ENABLE.getStatus());
            child.setOrgType("0");
            deptService.createDept(child);
            assertEquals(gen, cacheCoordinator.currentGeneration(), "mid-tx 不得 bump");
            assertNotNull(cache().get(parentId), "mid-tx 不得 clear");
            return null;
        });
        assertTrue(cacheCoordinator.currentGeneration() > gen);
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
        Cache cache = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        assertNotNull(cache);
        return cache;
    }

}
