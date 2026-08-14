package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
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
import cn.iocoder.yudao.module.system.service.mfa.delivery.StubMfaChallengeDelivery;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.mfa.store.InMemoryMfaAuthFlowStore;
import cn.iocoder.yudao.module.system.service.mfa.store.InMemoryMfaFactorStore;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 切片 4：enroll 失败窗口补偿、存储 CAS 边界、SMS/EMAIL 投递桩。
 */
public class MfaSlice4CompensationAndPersistenceTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyControlServiceImpl policyControl;
    private MfaAssuranceAuthorityImpl assurance;
    @Spy
    private MfaAuthFlowServiceImpl flowService = new MfaAuthFlowServiceImpl();
    @Spy
    private MfaFactorServiceImpl factorService = new MfaFactorServiceImpl(flowService);

    private MfaTokenIssuanceFacadeImpl facade;

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
        ReflectionTestUtils.setField(authService, "mfaTokenIssuanceFacade", facade);
        ReflectionTestUtils.setField(authService, "mfaAuthFlowService", flowService);
        ReflectionTestUtils.setField(authService, "mfaFactorService", factorService);
        lenient().when(oauth2TokenService.createAccessToken(anyLong(), anyInt(), anyString(), any()))
                .thenAnswer(inv -> token((Long) inv.getArgument(0)));
        MfaIssuanceGuard.clear();
    }

    @AfterEach
    void tearDown() {
        MfaIssuanceGuard.clear();
        flowService.clear();
        factorService.clear();
    }

    @Test
    void enroll_tokenFailure_keepsPending_retrySucceeds() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        String flowToken = startEnrollment(10L);
        AuthMfaEnrollmentTotpStartRespVO start = startTotp(flowToken, 10L);

        when(oauth2TokenService.createAccessToken(anyLong(), anyInt(), anyString(), any()))
                .thenThrow(new RuntimeException("token insert failed"))
                .thenAnswer(inv -> token((Long) inv.getArgument(0)));

        AuthMfaEnrollmentTotpConfirmReqVO confirm = confirmReq(flowToken, start, totp(start.getSecretManual()));
        assertThrows(RuntimeException.class, () -> authService.mfaEnrollmentTotpConfirm(confirm));
        assertEquals("PENDING", factorService.peekFactorStatus(1L, 10L, start.getFactorId()));
        assertFalse(factorService.hasActiveFactor(1L, 10L));
        assertNotNull(flowService.resolveActive(flowToken));

        AuthLoginRespVO loggedIn = authService.mfaEnrollmentTotpConfirm(confirm);
        assertEquals("AUTHENTICATED", loggedIn.getLoginStatus());
        assertEquals("ACTIVE", factorService.peekFactorStatus(1L, 10L, start.getFactorId()));
        assertNull(flowService.resolveActive(flowToken));
    }

    @Test
    void enroll_completeFailure_revertsActive_retrySucceeds() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        String flowToken = startEnrollment(10L);
        AuthMfaEnrollmentTotpStartRespVO start = startTotp(flowToken, 10L);

        doReturn(false).doCallRealMethod().when(flowService).tryComplete(anyString());

        AuthMfaEnrollmentTotpConfirmReqVO confirm = confirmReq(flowToken, start, totp(start.getSecretManual()));
        assertThrows(Exception.class, () -> authService.mfaEnrollmentTotpConfirm(confirm));
        assertEquals("PENDING", factorService.peekFactorStatus(1L, 10L, start.getFactorId()));
        verify(oauth2TokenService, atLeastOnce()).removeAccessToken(anyString());

        AuthLoginRespVO loggedIn = authService.mfaEnrollmentTotpConfirm(confirm);
        assertEquals("AUTHENTICATED", loggedIn.getLoginStatus());
        assertEquals("ACTIVE", factorService.peekFactorStatus(1L, 10L, start.getFactorId()));
    }

    @Test
    void flowStore_casComplete_oneShotAcrossInstances() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaAuthFlowServiceImpl a = new MfaAuthFlowServiceImpl(InMemoryMfaAuthFlowStore.shared());
        MfaAuthFlowServiceImpl b = new MfaAuthFlowServiceImpl(InMemoryMfaAuthFlowStore.shared());
        var issued = a.issue(cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass.PRE_AUTH,
                1L, 1L, "c", policyControl.resolveEffectivePolicy(1L), 0L, List.of(), List.of(), 60);
        assertTrue(a.tryComplete(issued.getFlowToken()));
        assertFalse(b.tryComplete(issued.getFlowToken()));
        assertNull(b.resolveActive(issued.getFlowToken()));
    }

    @Test
    void factorStore_casActivate_oneShot() {
        InMemoryMfaFactorStore s = InMemoryMfaFactorStore.shared();
        s.clear();
        factorService.startPendingTotp(1L, 8L, "bob");
        // use register to have known id
        factorService.registerActiveFactor(1L, 8L, "will-overwrite", "TOTP", "x");
        s.clear();
        var pending = factorService.startPendingTotp(1L, 8L, "bob");
        assertTrue(factorService.tryActivatePendingFactor(1L, 8L, pending.getFactorId(), 12L));
        assertFalse(factorService.tryActivatePendingFactor(1L, 8L, pending.getFactorId(), 12L));
        assertEquals("ACTIVE", factorService.peekFactorStatus(1L, 8L, pending.getFactorId()));
    }

    @Test
    void smsAndEmail_deliveryStubInvoked() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("SMS", "EMAIL"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("SMS", "EMAIL"));
        factorService.registerActiveFactor(1L, 10L, "sms-main", "SMS", "+8613800000000");
        factorService.registerActiveFactor(1L, 10L, "email-main", "EMAIL", "a@example.com");
        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));
        String flowToken = challenge.getLoginResp().getFlow().getFlowToken();

        AuthMfaCodeSendReqVO sms = new AuthMfaCodeSendReqVO();
        sms.setFlowToken(flowToken);
        sms.setFactorId("sms-main");
        authService.mfaSendCode(sms);
        AuthMfaCodeSendReqVO email = new AuthMfaCodeSendReqVO();
        email.setFlowToken(flowToken);
        email.setFactorId("email-main");
        authService.mfaSendCode(email);

        StubMfaChallengeDelivery stub = (StubMfaChallengeDelivery) factorService.challengeDelivery();
        assertEquals(2, stub.snapshot().size());
        assertEquals("SMS", stub.snapshot().get(0).factorType());
        assertEquals("EMAIL", stub.snapshot().get(1).factorType());

        String code = factorService.peekDeliveryCodeForTest(flowToken, "sms-main");
        AuthMfaVerifyReqVO verify = new AuthMfaVerifyReqVO();
        verify.setFlowToken(flowToken);
        verify.setFactorId("sms-main");
        verify.setCode(code);
        assertEquals("AUTHENTICATED", authService.mfaVerify(verify).getLoginStatus());
    }

    private String startEnrollment(long userId) {
        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, userId, 1L, UserTypeEnum.ADMIN.getValue(),
                OAuth2ClientConstants.CLIENT_ID_DEFAULT, null, List.of("pwd"));
        assertEquals(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED, challenge.getLoginStatus());
        return challenge.getLoginResp().getFlow().getFlowToken();
    }

    private AuthMfaEnrollmentTotpStartRespVO startTotp(String flowToken, long userId) {
        AdminUserDO user = new AdminUserDO();
        user.setId(userId);
        user.setUsername("alice");
        user.setTenantId(1L);
        when(userService.getUser(userId)).thenReturn(user);
        AuthMfaEnrollmentTotpStartReqVO startReq = new AuthMfaEnrollmentTotpStartReqVO();
        startReq.setFlowToken(flowToken);
        return authService.mfaEnrollmentTotpStart(startReq);
    }

    private static AuthMfaEnrollmentTotpConfirmReqVO confirmReq(String flowToken,
                                                                AuthMfaEnrollmentTotpStartRespVO start,
                                                                String code) {
        AuthMfaEnrollmentTotpConfirmReqVO confirm = new AuthMfaEnrollmentTotpConfirmReqVO();
        confirm.setFlowToken(flowToken);
        confirm.setFactorId(start.getFactorId());
        confirm.setCode(code);
        return confirm;
    }

    private static OAuth2AccessTokenDO token(Long uid) {
        OAuth2AccessTokenDO t = new OAuth2AccessTokenDO();
        t.setUserId(uid);
        t.setUserType(UserTypeEnum.ADMIN.getValue());
        t.setTenantId(1L);
        t.setAccessToken("access-" + uid);
        t.setRefreshToken("refresh-" + uid);
        t.setExpiresTime(LocalDateTime.now().plusHours(1));
        t.setUserInfo(new HashMap<>());
        return t;
    }

    private static String totp(String secret) {
        try {
            long step = java.time.Instant.now().getEpochSecond() / 30L;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
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
