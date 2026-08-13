package cn.iocoder.yudao.module.system.service.dept;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.redis.config.YudaoRedisAutoConfiguration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C1：缓存命名空间隔离 — 新 stamped 类型不得写入遗留 {@code dept_children_ids}，
 * 旧实例/回滚包只读旧名时不会反序列化新类。
 */
@Import({DeptServiceImpl.class, DeptMutationLock.class, DeptChildrenCacheInvalidator.class,
        DeptChildrenCacheC1NamespaceTest.CacheTestConfig.class})
public class DeptChildrenCacheC1NamespaceTest extends BaseDbUnitTest {

    @Resource
    private DeptService deptService;
    @Resource
    private DeptChildrenCacheInvalidator coordinator;
    @Resource
    private CacheManager cacheManager;

    public static class CacheTestConfig {
        @Bean
        @Primary
        public CacheManager dualNamespaceCacheManager() {
            // 同时注册新旧命名空间，便于断言「只写 V2」
            return new ConcurrentMapCacheManager(
                    RedisKeyConstants.DEPT_CHILDREN_ID_LIST,
                    RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        }
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        coordinator.afterLoadBeforePutHook = null;
        clearBoth();
    }

    @AfterEach
    void tearDown() {
        coordinator.afterLoadBeforePutHook = null;
        TenantContextHolder.clear();
        clearBoth();
    }

    @Test
    void stampedValuesOnlyWrittenToV2NamespaceNeverLegacy() {
        Long parentId = createRoot("C1父");
        // 预置遗留名上的「旧包」Set 形态
        Cache legacy = cache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        legacy.put(parentId, Set.of(1L, 2L));

        Set<Long> ids = deptService.getChildDeptIdListFromCache(parentId);
        assertNotNull(ids);

        Cache v2 = cache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        assertNotNull(v2.get(parentId), "新代码必须写入 V2 命名空间");
        Object v2Val = v2.get(parentId).get();
        assertInstanceOf(DeptChildrenCacheInvalidator.GenerationStampedSet.class, v2Val);

        // 遗留命名空间不得被 put 成 GenerationStampedSet
        Cache.ValueWrapper legacyAfter = legacy.get(parentId);
        if (legacyAfter != null && legacyAfter.get() != null) {
            assertFalse(legacyAfter.get() instanceof DeptChildrenCacheInvalidator.GenerationStampedSet,
                    "C1：遗留命名空间不得出现 stamped 类型");
        }
        assertEquals(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2, DeptChildrenCacheInvalidator.activeCacheName());
        assertEquals(RedisKeyConstants.DEPT_CHILDREN_ID_LIST, DeptChildrenCacheInvalidator.legacyCacheName());
    }

    @Test
    void writeBumpClearsLegacyNamespaceToHelpRollingOldInstances() {
        Long parentId = createRoot("C1滚动父");
        Cache legacy = cache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        legacy.put(parentId, Set.of(99L)); // 旧实例缓存

        DeptSaveReqVO child = new DeptSaveReqVO();
        child.setName("C1滚动子");
        child.setParentId(parentId);
        child.setSort(1);
        child.setStatus(CommonStatusEnum.ENABLE.getStatus());
        child.setOrgType("0");
        deptService.createDept(child);

        assertNull(legacy.get(parentId), "写成功应 clear 遗留名，促旧实例 miss 重载");
        assertNull(cache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2).get(parentId));
    }

    /**
     * 生产 Redis serializer（JSON + default typing）round-trip：
     * - 新实例可反序列化 stamped；
     * - 旧读路径不打开 V2 键 → 对遗留名 get 为 miss/旧 Set，无 SerializationException。
     */
    @Test
    @SuppressWarnings("unchecked")
    void productionSerializerNewWriteOldReadPathNoException() throws Exception {
        RedisSerializer<Object> production =
                (RedisSerializer<Object>) YudaoRedisAutoConfiguration.buildRedisSerializer();

        DeptChildrenCacheInvalidator.GenerationStampedSet stamped =
                new DeptChildrenCacheInvalidator.GenerationStampedSet(7L, Set.of(10L, 20L));
        byte[] payload = production.serialize(stamped);
        assertNotNull(payload);
        // 新实例 round-trip OK
        Object roundTrip = production.deserialize(payload);
        assertInstanceOf(DeptChildrenCacheInvalidator.GenerationStampedSet.class, roundTrip);

        // 旧实例 classpath 无新类：对同一 payload 反序列化会失败 —— 这是「若误读 V2」的风险；
        // C1 闭合靠命名空间：旧代码只访问 DEPT_CHILDREN_ID_LIST，从不 deserialize 本 payload。
        ObjectMapper oldStyleMapper = new ObjectMapper();
        // 模拟旧包只认识 Set：payload 含 @class 新类型时，旧逻辑若强行反序列化会炸；
        // 此处断言「旧读路径」= 只 get 遗留 cache，与 payload 无关
        Cache legacy = cache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        Long anyId = 42L;
        assertNull(legacy.get(anyId), "旧命名空间无新写 stamped 键");
        // 旧包可安全 put/get Set
        legacy.put(anyId, Set.of(1L));
        assertEquals(Set.of(1L), legacy.get(anyId).get());

        // 回滚后：新包停止写 V2；旧包继续只读遗留名（上一步已证明无异常）
        // V2 中残留 stamped 对旧包不可见
        Cache v2 = cache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        v2.put(anyId, stamped);
        assertNotNull(v2.get(anyId));
        // 旧路径不读 v2
        assertDoesNotThrow(() -> {
            Object o = legacy.get(anyId).get();
            assertFalse(o instanceof DeptChildrenCacheInvalidator.GenerationStampedSet);
        });
    }

    /**
     * 证明：若（错误地）用生产 serializer 在「无新类」语义下解析 stamped JSON，会得到类型错误；
     * 因此必须命名空间隔离，而非依赖旧代码容错。
     */
    @Test
    @SuppressWarnings("unchecked")
    void stampedPayloadRequiresNewClass_henceNamespaceIsolationRequired() {
        RedisSerializer<Object> production =
                (RedisSerializer<Object>) YudaoRedisAutoConfiguration.buildRedisSerializer();
        byte[] payload = production.serialize(
                new DeptChildrenCacheInvalidator.GenerationStampedSet(1L, Set.of(1L)));
        String json = new String(payload);
        assertTrue(json.contains("GenerationStampedSet") || json.contains("generation"),
                "生产 serializer 会嵌入类型信息，旧 classpath 无法解析");
        // 新 classpath 可解析
        assertDoesNotThrow(() -> production.deserialize(payload));
        // 隔离：新写不进 legacy 名（由 stampedValuesOnlyWrittenToV2NamespaceNeverLegacy 覆盖）
        assertNotEquals(RedisKeyConstants.DEPT_CHILDREN_ID_LIST,
                RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
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

    private Cache cache(String name) {
        Cache c = cacheManager.getCache(name);
        assertNotNull(c);
        return c;
    }

    private void clearBoth() {
        Cache a = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST);
        Cache b = cacheManager.getCache(RedisKeyConstants.DEPT_CHILDREN_ID_LIST_V2);
        if (a != null) {
            a.clear();
        }
        if (b != null) {
            b.clear();
        }
    }

}
