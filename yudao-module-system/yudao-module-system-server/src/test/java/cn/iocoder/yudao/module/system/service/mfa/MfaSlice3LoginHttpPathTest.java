package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaCodeSendReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaEnrollmentTotpConfirmReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaEnrollmentTotpStartReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaEnrollmentTotpStartRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaVerifyReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthServiceImpl;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaIssuanceGuard;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_FACTOR_VERIFY_FAILED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_FLOW_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 切片 3：登录 HTTP 全链路（challenge → enroll/verify → issue）+ Guard 元数据。
 */
public class MfaSlice3LoginHttpPathTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyControlServiceImpl policyControl;
    private MfaAssuranceAuthorityImpl assurance;
    @Spy
    private MfaAuthFlowServiceImpl flowService = new MfaAuthFlowServiceImpl();
    @Spy
    private MfaFactorServiceImpl factorService = new MfaFactorServiceImpl(flowService);

    private MfaTokenIssuanceFacadeImpl facade;
    private MfaSessionGuardImpl sessionGuard;

    @Mock
    private AdminUserService userService;
    @Mock
    private OAuth2TokenService oauth2TokenService;

    @InjectMocks
    private AdminAuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policyControl = new MfaPolicyControlServiceImpl(new MfaPolicyAuthorityImpl(store));
        assurance = new MfaAssuranceAuthorityImpl(store);
        flowService.clear();
        factorService.clear();
        facade = new MfaTokenIssuanceFacadeImpl();
        ReflectionTestUtils.setField(facade, "policyControlService", policyControl);
        ReflectionTestUtils.setField(facade, "authFlowService", flowService);
        ReflectionTestUtils.setField(facade, "factorService", factorService);
        ReflectionTestUtils.setField(facade, "assuranceAuthority", assurance);
        ReflectionTestUtils.setField(facade, "userFactorProbe", new MfaUserFactorProbeImpl(factorService));
        ReflectionTestUtils.setField(facade, "oauth2TokenService", oauth2TokenService);

        sessionGuard = new MfaSessionGuardImpl();
        ReflectionTestUtils.setField(sessionGuard, "policyControlService", policyControl);
        ReflectionTestUtils.setField(sessionGuard, "assuranceAuthority", assurance);

        ReflectionTestUtils.setField(authService, "mfaTokenIssuanceFacade", facade);
        ReflectionTestUtils.setField(authService, "mfaAuthFlowService", flowService);
        ReflectionTestUtils.setField(authService, "mfaFactorService", factorService);

        lenient().when(oauth2TokenService.createAccessToken(anyLong(), anyInt(), anyString(), any()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    OAuth2AccessTokenDO t = new OAuth2AccessTokenDO();
                    t.setUserId(uid);
                    t.setUserType(UserTypeEnum.ADMIN.getValue());
                    t.setTenantId(1L);
                    t.setAccessToken("access-" + uid);
                    t.setRefreshToken("refresh-" + uid);
                    t.setExpiresTime(LocalDateTime.now().plusHours(1));
                    t.setUserInfo(new HashMap<>());
                    return t;
                });
        MfaIssuanceGuard.clear();
    }

    @AfterEach
    void tearDown() {
        MfaIssuanceGuard.clear();
        flowService.clear();
        factorService.clear();
    }

    @Test
    void enrollmentPath_totpStartConfirm_issuesToken() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        // no active factor → ENROLLMENT
        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                OAuth2ClientConstants.CLIENT_ID_DEFAULT, null, List.of("pwd"));
        assertEquals(MfaIssuanceOutcome.CHALLENGE, challenge.getOutcome());
        assertEquals(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED, challenge.getLoginStatus());
        String flowToken = challenge.getLoginResp().getFlow().getFlowToken();
        assertEquals("ENROLLMENT", challenge.getLoginResp().getFlow().getTokenClass());

        AdminUserDO user = new AdminUserDO();
        user.setId(10L);
        user.setUsername("alice");
        user.setTenantId(1L);
        when(userService.getUser(10L)).thenReturn(user);

        AuthMfaEnrollmentTotpStartReqVO startReq = new AuthMfaEnrollmentTotpStartReqVO();
        startReq.setFlowToken(flowToken);
        AuthMfaEnrollmentTotpStartRespVO start = authService.mfaEnrollmentTotpStart(startReq);
        assertNotNull(start.getSecretManual());
        assertNotNull(start.getOtpauthUri());
        assertTrue(start.getOtpauthUri().startsWith("otpauth://totp/"));

        String code = totp(start.getSecretManual());
        AuthMfaEnrollmentTotpConfirmReqVO confirmReq = new AuthMfaEnrollmentTotpConfirmReqVO();
        confirmReq.setFlowToken(flowToken);
        confirmReq.setFactorId(start.getFactorId());
        confirmReq.setCode(code);
        AuthLoginRespVO loggedIn = authService.mfaEnrollmentTotpConfirm(confirmReq);
        assertEquals("AUTHENTICATED", loggedIn.getLoginStatus());
        assertNotNull(loggedIn.getAccessToken());
        assertNull(loggedIn.getFlow());
        assertTrue(factorService.hasActiveFactor(1L, 10L));
        assertNull(flowService.resolveActive(flowToken));
    }

    @Test
    void verifyPath_preAuth_withActiveFactor() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        factorService.registerActiveFactor(1L, 10L, "totp-main", "TOTP", "verify-secret");

        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));
        assertEquals(MfaLoginStatus.MFA_REQUIRED, challenge.getLoginStatus());
        assertFalse(challenge.getLoginResp().getFlow().getFactors().isEmpty());
        String flowToken = challenge.getLoginResp().getFlow().getFlowToken();

        AuthMfaVerifyReqVO verify = new AuthMfaVerifyReqVO();
        verify.setFlowToken(flowToken);
        verify.setFactorId("totp-main");
        verify.setCode(totp("verify-secret"));
        AuthLoginRespVO resp = authService.mfaVerify(verify);
        assertEquals("AUTHENTICATED", resp.getLoginStatus());
        assertNotNull(resp.getAccessToken());
    }

    @Test
    void issuedToken_passesSessionGuard() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        when(oauth2TokenService.createAccessToken(anyLong(), anyInt(), anyString(), any()))
                .thenAnswer(inv -> {
                    assertNotNull(MfaIssuanceGuard.current());
                    OAuth2AccessTokenDO t = new OAuth2AccessTokenDO();
                    t.setUserId(inv.getArgument(0));
                    t.setUserType(UserTypeEnum.ADMIN.getValue());
                    t.setTenantId(1L);
                    t.setAccessToken("a");
                    t.setRefreshToken("r");
                    t.setUserInfo(new HashMap<>());
                    return t;
                });
        MfaIssuanceResult r = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 7L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));
        assertEquals(MfaIssuanceOutcome.ALLOWED, r.getOutcome());
        // metadata stamped
        assertNotNull(r.getAccessToken().getUserInfo().get(MfaSessionGuardImpl.UI_TOKEN_CLASS));
        sessionGuard.assertAccessAllowed(r.getAccessToken());
    }

    @Test
    void verify_invalidFlow_rejected() {
        AuthMfaVerifyReqVO verify = new AuthMfaVerifyReqVO();
        verify.setFlowToken("missing");
        verify.setFactorId("totp-main");
        verify.setCode("000000");
        ServiceException ex = assertThrows(ServiceException.class, () -> authService.mfaVerify(verify));
        assertEquals(MFA_FLOW_INVALID.getCode(), ex.getCode());
    }

    @Test
    void verify_wrongTotp_rejected_flowRemainsActive() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        factorService.registerActiveFactor(1L, 10L, "totp-main", "TOTP", "verify-secret");
        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));
        String flowToken = challenge.getLoginResp().getFlow().getFlowToken();

        AuthMfaVerifyReqVO verify = new AuthMfaVerifyReqVO();
        verify.setFlowToken(flowToken);
        verify.setFactorId("totp-main");
        verify.setCode("000000");
        ServiceException ex = assertThrows(ServiceException.class, () -> authService.mfaVerify(verify));
        assertEquals(MFA_FACTOR_VERIFY_FAILED.getCode(), ex.getCode());
        assertNotNull(flowService.resolveActive(flowToken));
    }

    @Test
    void enrollStart_onPreAuthFlow_rejected() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        factorService.registerActiveFactor(1L, 10L, "totp-main", "TOTP", "verify-secret");
        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));
        assertEquals(MfaLoginStatus.MFA_REQUIRED, challenge.getLoginStatus());

        AuthMfaEnrollmentTotpStartReqVO startReq = new AuthMfaEnrollmentTotpStartReqVO();
        startReq.setFlowToken(challenge.getLoginResp().getFlow().getFlowToken());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> authService.mfaEnrollmentTotpStart(startReq));
        assertEquals(MFA_FLOW_INVALID.getCode(), ex.getCode());
    }

    @Test
    void verify_onEnrollmentFlow_rejected() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));
        assertEquals(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED, challenge.getLoginStatus());

        String flowToken = challenge.getLoginResp().getFlow().getFlowToken();
        ServiceException ex = assertThrows(ServiceException.class, () ->
                facade.completeChallengeAndIssue(flowToken, "totp-main", "TOTP", "000000",
                        "default", null));
        assertEquals(MFA_FLOW_INVALID.getCode(), ex.getCode());
        assertNotNull(flowService.resolveActive(flowToken));
    }

    @Test
    void smsSendAndVerify_stubChannel() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("SMS"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("SMS"));
        factorService.registerActiveFactor(1L, 10L, "sms-main", "SMS", "+8613800000000");
        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));
        String flowToken = challenge.getLoginResp().getFlow().getFlowToken();

        AuthMfaCodeSendReqVO send = new AuthMfaCodeSendReqVO();
        send.setFlowToken(flowToken);
        send.setFactorId("sms-main");
        authService.mfaSendCode(send);
        String code = factorService.peekDeliveryCodeForTest(flowToken, "sms-main");
        assertNotNull(code);

        AuthMfaVerifyReqVO verify = new AuthMfaVerifyReqVO();
        verify.setFlowToken(flowToken);
        verify.setFactorId("sms-main");
        verify.setCode(code);
        AuthLoginRespVO resp = authService.mfaVerify(verify);
        assertEquals("AUTHENTICATED", resp.getLoginStatus());
        assertNotNull(resp.getAccessToken());
    }

    @Test
    void refresh_stampsGuardMetadata() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        OAuth2AccessTokenDO refreshed = new OAuth2AccessTokenDO();
        refreshed.setUserId(7L);
        refreshed.setUserType(UserTypeEnum.ADMIN.getValue());
        refreshed.setTenantId(1L);
        refreshed.setAccessToken("new-a");
        refreshed.setRefreshToken("r");
        refreshed.setUserInfo(new HashMap<>());
        when(oauth2TokenService.refreshAccessToken(eq("r"), anyString())).thenReturn(refreshed);

        MfaIssuanceResult result = facade.refresh(MfaIssuancePath.REFRESH_TOKEN, "r", "default");
        assertEquals(MfaIssuanceOutcome.ALLOWED, result.getOutcome());
        assertNotNull(result.getAccessToken().getUserInfo().get(MfaSessionGuardImpl.UI_TOKEN_CLASS));
        assertNotNull(result.getAccessToken().getUserInfo().get(MfaSessionGuardImpl.UI_GLOBAL_EPOCH));
        sessionGuard.assertAccessAllowed(result.getAccessToken());
    }

    @Test
    void nestedFlowJson_contract() {
        AuthLoginRespVO vo = AuthLoginRespVO.builder()
                .loginStatus("MFA_REQUIRED")
                .flow(AuthLoginRespVO.FlowPayload.builder()
                        .flowToken("x").tokenClass("PRE_AUTH").expiresIn(300)
                        .allowedActions(List.of("verify"))
                        .build())
                .build();
        String json = JsonUtils.toJsonString(vo);
        assertTrue(json.contains("\"flow\""));
        assertFalse(json.contains("challengeToken"));
    }

    private static String totp(String secret) {
        try {
            long step = java.time.Instant.now().getEpochSecond() / 30L;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
