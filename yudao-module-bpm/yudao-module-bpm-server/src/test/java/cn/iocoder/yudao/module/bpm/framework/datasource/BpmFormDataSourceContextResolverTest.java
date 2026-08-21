package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link BpmFormDataSourceContextResolver} 的单元测试
 */
class BpmFormDataSourceContextResolverTest {

    private BpmFormDataSourceContextResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BpmFormDataSourceContextResolver();
    }

    // ==================== 正常解析服务端上下文 ====================

    @Test
    void resolvesServerContext() {
        LoginUser user = buildLoginUser(100L, 1L, "200", 300L);
        Map<String, Object> request = new HashMap<>();
        request.put("keyword", "test");

        Map<String, Object> result = resolver.resolve(request, user);

        assertEquals(1L, result.get("tenantId"));
        assertEquals(100L, result.get("userId"));
        assertEquals("200", result.get("deptId"));
        assertEquals(300L, result.get("companyId"));
        // client parameter preserved
        assertEquals("test", result.get("keyword"));
    }

    // ==================== 拒绝浏览器覆盖保留参数 ====================

    @Test
    void rejectsClientOverrideOfTenantId() {
        LoginUser user = buildLoginUser(100L, 1L, "200", 300L);
        Map<String, Object> request = new HashMap<>();
        request.put("tenantId", 999L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.resolve(request, user));
        assertEquals(BPM_DATA_SOURCE_PARAM_RESERVED.getCode(), ex.getCode());
    }

    @Test
    void rejectsClientOverrideOfUserId() {
        LoginUser user = buildLoginUser(100L, 1L, "200", 300L);
        Map<String, Object> request = new HashMap<>();
        request.put("userId", 999L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.resolve(request, user));
        assertEquals(BPM_DATA_SOURCE_PARAM_RESERVED.getCode(), ex.getCode());
    }

    @Test
    void rejectsClientOverrideOfDeptId() {
        LoginUser user = buildLoginUser(100L, 1L, "200", 300L);
        Map<String, Object> request = new HashMap<>();
        request.put("deptId", 999L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.resolve(request, user));
        assertEquals(BPM_DATA_SOURCE_PARAM_RESERVED.getCode(), ex.getCode());
    }

    @Test
    void rejectsClientOverrideOfCompanyId() {
        LoginUser user = buildLoginUser(100L, 1L, "200", 300L);
        Map<String, Object> request = new HashMap<>();
        request.put("companyId", 999L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.resolve(request, user));
        assertEquals(BPM_DATA_SOURCE_PARAM_RESERVED.getCode(), ex.getCode());
    }

    @Test
    void rejectsClientOverrideOfSharedInstanceIds() {
        LoginUser user = buildLoginUser(100L, 1L, "200", 300L);
        Map<String, Object> request = new HashMap<>();
        request.put("sharedInstanceIds", List.of("hack"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> resolver.resolve(request, user));
        assertEquals(BPM_DATA_SOURCE_PARAM_RESERVED.getCode(), ex.getCode());
    }

    // ==================== 空请求参数正常工作 ====================

    @Test
    void resolvesWithEmptyRequest() {
        LoginUser user = buildLoginUser(100L, 1L, "200", 300L);
        Map<String, Object> request = new HashMap<>();

        Map<String, Object> result = resolver.resolve(request, user);

        assertEquals(1L, result.get("tenantId"));
        assertEquals(100L, result.get("userId"));
        assertEquals(4, result.size()); // tenantId, userId, deptId, companyId
    }

    @Test
    void resolvesWithNullRequest() {
        LoginUser user = buildLoginUser(100L, 1L, "200", 300L);

        Map<String, Object> result = resolver.resolve(null, user);

        assertEquals(1L, result.get("tenantId"));
        assertEquals(100L, result.get("userId"));
    }

    // ==================== Helper ====================

    private LoginUser buildLoginUser(Long id, Long tenantId, String deptId, Long companyId) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setTenantId(tenantId);
        Map<String, String> info = new HashMap<>();
        info.put("deptId", deptId);
        user.setInfo(info);
        user.setContext("companyId", companyId);
        return user;
    }
}
