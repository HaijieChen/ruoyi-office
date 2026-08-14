package cn.iocoder.yudao.module.bpm.framework.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityTokens;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.bpm.service.definition.BpmBusinessStartChannelHolder;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessStartEligibilityService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessStartEligibilityServiceImpl;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EXP-87 G1 边界：匿名/伪造/非 Finance 拒绝；真实 Finance 身份可进入 callTrusted；
 * 通用 create 对薪税仍 deny。
 */
@ExtendWith(MockitoExtension.class)
class BpmBusinessStartIdentityBoundaryTest {

    /** 测试注入的强密钥（≥24，非黑名单）；非源码默认值 */
    private static final String SECRET = "prod-injected-rpc-service-identity-key-9f3a";
    private static final String SALARY_KEY = "finance_salary_payment_apply";

    private RpcServiceIdentityProperties properties;
    private BpmBusinessStartCallerGuard guard;
    private BpmBusinessStartIdentityFilter filter;

    @Mock
    private SecurityFrameworkService securityFrameworkService;

    private BpmProcessStartEligibilityService eligibility;

    @BeforeEach
    void setUp() {
        properties = new RpcServiceIdentityProperties();
        properties.setSecret(SECRET);
        properties.setEnabled(true);
        guard = new BpmBusinessStartCallerGuard(properties);
        filter = new BpmBusinessStartIdentityFilter(properties);
        eligibility = new BpmProcessStartEligibilityServiceImpl(securityFrameworkService);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void filter_anonymous_returns401_andDoesNotSetAuthority() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/rpc-api/bpm/process-instance/create-by-business");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, (r, s) -> fail("must not continue filter chain"));
        assertEquals(401, resp.getStatus());
        assertFalse(BpmBusinessStartCallerIdentity.hasFinanceAuthority());
        assertTrue(resp.getContentAsString().contains("401")
                || resp.getContentAsString().contains("服务身份"));
    }

    @Test
    void filter_forgedToken_returns403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/rpc-api/bpm/process-instance/create-by-business");
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, "forged-not-hmac");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, (r, s) -> fail("must not continue"));
        assertEquals(403, resp.getStatus());
        assertFalse(BpmBusinessStartCallerIdentity.hasFinanceAuthority());
    }

    @Test
    void filter_nonFinanceServiceName_returns403() throws Exception {
        String token = RpcServiceIdentityTokens.sign("crm-server", RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS, SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/rpc-api/bpm/process-instance/create-by-business");
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME, "crm-server");
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, (r, s) -> fail("must not continue"));
        assertEquals(403, resp.getStatus());
    }

    @Test
    void filter_validFinanceIdentity_continuesAndSetsAuthority() throws Exception {
        String token = RpcServiceIdentityTokens.sign(RpcServiceIdentityConstants.FINANCE_SERVER, RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS, SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/rpc-api/bpm/process-instance/create-by-business");
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        final boolean[] continued = {false};
        filter.doFilter(req, resp, (r, s) -> {
            continued[0] = true;
            assertTrue(BpmBusinessStartCallerIdentity.hasFinanceAuthority());
        });
        assertTrue(continued[0]);
        assertEquals(200, resp.getStatus()); // default
    }

    @Test
    void guard_anonymous_throwsBeforeCallTrusted() {
        ServiceException ex = assertThrows(ServiceException.class, guard::requireVerifiedFinanceCaller);
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_BUSINESS_START_CALLER_FORBIDDEN.getCode(),
                ex.getCode());
        assertFalse(BpmBusinessStartChannelHolder.isTrustedBusinessStart());
    }

    @Test
    void guard_forgedHeader_throws() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, "bad");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        assertThrows(ServiceException.class, guard::requireVerifiedFinanceCaller);
    }

    @Test
    void guard_validFinanceHeader_allows() {
        String token = RpcServiceIdentityTokens.sign(RpcServiceIdentityConstants.FINANCE_SERVER, RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS, SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        assertDoesNotThrow(guard::requireVerifiedFinanceCaller);
    }

    @Test
    void service_createByBusiness_rejectsWithoutFinanceIdentity_neverCallTrusted() {
        BpmProcessInstanceServiceImpl service = new BpmProcessInstanceServiceImpl();
        ReflectionTestUtils.setField(service, "businessStartCallerGuard", guard);
        BpmProcessInstanceCreateReqDTO dto = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(SALARY_KEY)
                .setBusinessKey("1");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createProcessInstanceByBusiness(1L, dto));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_BUSINESS_START_CALLER_FORBIDDEN.getCode(),
                ex.getCode());
        assertFalse(BpmBusinessStartChannelHolder.isTrustedBusinessStart());
    }

    @Test
    void service_createByBusiness_withFinanceIdentity_entersTrustedChannel() {
        String token = RpcServiceIdentityTokens.sign(RpcServiceIdentityConstants.FINANCE_SERVER, RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS, SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        BpmProcessInstanceServiceImpl service = spy(new BpmProcessInstanceServiceImpl());
        ReflectionTestUtils.setField(service, "businessStartCallerGuard", guard);
        // stub generic createProcessInstance DTO path after trust elevated
        doReturn("pi-ok").when(service).createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class));

        BpmProcessInstanceCreateReqDTO dto = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey(SALARY_KEY)
                .setBusinessKey("1");
        String pi = service.createProcessInstanceByBusiness(1L, dto);
        assertEquals("pi-ok", pi);
        // callTrusted 执行后 ThreadLocal 已清理
        assertFalse(BpmBusinessStartChannelHolder.isTrustedBusinessStart());
        verify(service).createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class));
    }

    @Test
    void genericEligibility_salaryStillDeniedWithoutTrustedChannel() {
        assertFalse(BpmBusinessStartChannelHolder.isTrustedBusinessStart());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> eligibility.validateStartOrThrow(SALARY_KEY, false));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED.getCode(), ex.getCode());
    }

    @Test
    void financeIdentity_plusTrustedChannel_salaryAllowedWithPermission() {
        when(securityFrameworkService.hasPermission(eq("finance:salary-payment:create")))
                .thenReturn(true);
        String token = RpcServiceIdentityTokens.sign(RpcServiceIdentityConstants.FINANCE_SERVER, RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS, SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        assertDoesNotThrow(guard::requireVerifiedFinanceCaller);
        assertDoesNotThrow(() -> BpmBusinessStartChannelHolder.callTrusted(() -> {
            eligibility.validateStartOrThrow(SALARY_KEY, true);
            return null;
        }));
    }

    @Test
    void filter_withWeakOrMissingServerSecret_rejectsEvenWithForgedIdentityHeaders() throws Exception {
        RpcServiceIdentityProperties weak = new RpcServiceIdentityProperties();
        weak.setEnabled(true);
        weak.setSecret("yudao-rpc-service-identity-dev-only");
        BpmBusinessStartIdentityFilter weakFilter = new BpmBusinessStartIdentityFilter(weak);
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/rpc-api/bpm/process-instance/create-by-business");
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, "anything");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        weakFilter.doFilter(req, resp, (r, s) -> fail("must not continue with weak secret"));
        assertEquals(403, resp.getStatus());
    }

    @Test
    void filter_validFinanceWithInjectedStrongSecret_success_forgedStill403() throws Exception {
        // 合法
        String token = RpcServiceIdentityTokens.sign(RpcServiceIdentityConstants.FINANCE_SERVER, RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS, SECRET);
        MockHttpServletRequest ok = new MockHttpServletRequest("POST",
                "/rpc-api/bpm/process-instance/create-by-business");
        ok.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        ok.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
        MockHttpServletResponse okResp = new MockHttpServletResponse();
        final boolean[] continued = {false};
        filter.doFilter(ok, okResp, (r, s) -> continued[0] = true);
        assertTrue(continued[0]);

        // 伪造
        MockHttpServletRequest bad = new MockHttpServletRequest("POST",
                "/rpc-api/bpm/process-instance/create-by-business");
        bad.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        bad.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, "forged");
        MockHttpServletResponse badResp = new MockHttpServletResponse();
        filter.doFilter(bad, badResp, (r, s) -> fail("forged must not continue"));
        assertEquals(403, badResp.getStatus());
    }

    @Test
    void filter_tokenCapturedFromOtherRoute_cannotAuthorizeCreateByBusiness() throws Exception {
        // 从 generic create 路由截获的合法 HMAC 不得授权 privileged 路径
        String otherAudience = "POST /rpc-api/bpm/process-instance/create";
        String replayToken = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, otherAudience, SECRET);
        MockHttpServletRequest req = new MockHttpServletRequest("POST",
                "/rpc-api/bpm/process-instance/create-by-business");
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME,
                RpcServiceIdentityConstants.FINANCE_SERVER);
        req.addHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, replayToken);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, (r, s) -> fail("replay token must not continue"));
        assertEquals(403, resp.getStatus());
    }
}
