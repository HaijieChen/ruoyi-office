package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChallengeHandleStore;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaIssuanceGuard;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaTokenClassGuard;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F-01 用例（本切片可测）+ 默认 OFF 兼容 + 零 Token 真值。
 */
public class MfaTokenIssuanceFacadeTest extends BaseMockitoUnitTest {

    @Mock
    private MfaPolicyControlService policyControlService;
    @Mock
    private OAuth2TokenService oauth2TokenService;
    @Mock
    private MfaUserFactorProbe userFactorProbe;
    @Spy
    private MfaChallengeHandleStore challengeHandleStore = new MfaChallengeHandleStore();

    @InjectMocks
    private MfaTokenIssuanceFacadeImpl facade;

    private InMemoryMfaPolicyStore store;
    private MfaPolicyControlServiceImpl realPolicy;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaPolicyStore();
        realPolicy = new MfaPolicyControlServiceImpl(store);
        // 默认：把 policyControlService 委托给真实控制面（UNINITIALIZED/OFF）
        lenient().when(policyControlService.resolveEffectivePolicy(any())).thenAnswer(inv ->
                realPolicy.resolveEffectivePolicy(inv.getArgument(0)));
        lenient().when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);
        lenient().when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(false);
        lenient().when(userFactorProbe.isUserMfaEnabled(any(), any())).thenReturn(false);
        lenient().when(userFactorProbe.isEnrollmentPendingOnSession(anyString())).thenReturn(false);
        lenient().when(userFactorProbe.isRecoveryPendingOnSession(anyString())).thenReturn(false);
        MfaIssuanceGuard.clear();
    }

    @AfterEach
    void tearDown() {
        MfaIssuanceGuard.clear();
        challengeHandleStore.clear();
    }

    private OAuth2AccessTokenDO tokenFor(Long userId) {
        OAuth2AccessTokenDO t = new OAuth2AccessTokenDO();
        t.setUserId(userId);
        t.setUserType(UserTypeEnum.ADMIN.getValue());
        t.setAccessToken("access-" + userId);
        t.setRefreshToken("refresh-" + userId);
        t.setExpiresTime(LocalDateTime.now().plusHours(1));
        t.setUserInfo(new HashMap<>());
        return t;
    }

    private void stubCreateIssuesToken() {
        when(oauth2TokenService.createAccessToken(anyLong(), anyInt(), anyString(), any()))
                .thenAnswer(inv -> {
                    Long uid = inv.getArgument(0);
                    Integer ut = inv.getArgument(1);
                    if (uid != null && uid != 0L && UserTypeEnum.ADMIN.getValue().equals(ut)) {
                        assertNotNull(MfaIssuanceGuard.current());
                        assertTrue(MfaIssuanceGuard.permitAdminIssuance(uid, ut));
                    }
                    return tokenFor(uid == null ? 0L : uid);
                });
    }

    /** F-01 默认 OFF：login 路径 ALLOWED 且有 token */
    @ParameterizedTest
    @EnumSource(value = MfaIssuancePath.class, names = {
            "LOGIN_PASSWORD", "LOGIN_SMS", "LOGIN_SOCIAL", "REGISTER"
    })
    void f01_off_interactiveAllowed(MfaIssuancePath path) {
        stubCreateIssuesToken();
        MfaIssuanceResult result = facade.issueAfterPrimaryAuth(
                path, 10L, 1L, UserTypeEnum.ADMIN.getValue(), "default", null, List.of("pwd"));
        assertEquals(MfaIssuanceOutcome.ALLOWED, result.getOutcome());
        assertTrue(result.hasAccessOrRefreshToken());
        assertEquals(MfaLoginStatus.AUTHENTICATED, result.getLoginStatus());
        assertEquals("access-10", result.getLoginResp().getAccessToken());
    }

    /** F-01-1: 需 MFA 时仅 challenge，零 access/refresh */
    @ParameterizedTest
    @EnumSource(value = MfaIssuancePath.class, names = {
            "LOGIN_PASSWORD", "LOGIN_SMS", "LOGIN_SOCIAL"
    })
    void f01_1_requiredChallengeZeroToken(MfaIssuancePath path) {
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(true);
        when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);

        MfaIssuanceResult result = facade.issueAfterPrimaryAuth(
                path, 10L, 1L, UserTypeEnum.ADMIN.getValue(), "default", null, List.of("pwd"));

        assertEquals(MfaIssuanceOutcome.CHALLENGE, result.getOutcome());
        assertEquals(MfaLoginStatus.MFA_REQUIRED, result.getLoginStatus());
        assertFalse(result.hasAccessOrRefreshToken());
        assertNotNull(result.getLoginResp().getChallengeToken());
        assertNull(result.getLoginResp().getAccessToken());
        assertNull(result.getLoginResp().getRefreshToken());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    /** F-01-2: register + enrollment required */
    @Test
    void f01_2_registerEnrollmentRequiredZeroToken() {
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(false);
        // isEnrollmentComplete 在无合格因子时短路，不必再 stub

        MfaIssuanceResult result = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.REGISTER, 11L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));

        assertEquals(MfaIssuanceOutcome.CHALLENGE, result.getOutcome());
        assertEquals(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED, result.getLoginStatus());
        assertFalse(result.hasAccessOrRefreshToken());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    /** F-01-7: password grant 需 MFA → REJECT */
    @Test
    void f01_7_passwordGrantRejectWhenMfaRequired() {
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(true);
        when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                facade.issueForOAuthGrant(MfaIssuancePath.OAUTH2_PASSWORD, 10L, 1L,
                        UserTypeEnum.ADMIN.getValue(), "default", null, List.of("pwd")));
        assertEquals(MFA_TOKEN_ISSUANCE_REJECTED.getCode(), ex.getCode());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    /** F-01-7: client_credentials N/A 成功 */
    @Test
    void f01_7_clientCredentialsNa() {
        when(oauth2TokenService.createAccessToken(eq(0L), anyInt(), anyString(), any()))
                .thenReturn(tokenFor(0L));
        OAuth2AccessTokenDO token = facade.issueClientCredentials("client", List.of("read"));
        assertNotNull(token.getAccessToken());
        verify(oauth2TokenService).createAccessToken(eq(0L), eq(UserTypeEnum.ADMIN.getValue()),
                eq("client"), any());
    }

    /** F-01-8: 内部 create ADMIN 拒绝 */
    @Test
    void f01_8_internalAdminCreateRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
                facade.rejectInternalAdminCreate(99L, UserTypeEnum.ADMIN.getValue()));
        assertEquals(MFA_ADMIN_DIRECT_ISSUE_FORBIDDEN.getCode(), ex.getCode());
    }

    /** DEGRADED_CLOSED 下 login/refresh 失败 */
    @Test
    void f03_degradedClosedRejectsLoginAndRefresh() {
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        store.corruptGlobalMode("NOPE");
        realPolicy.invalidateCache();

        assertThrows(ServiceException.class, () ->
                facade.issueAfterPrimaryAuth(MfaIssuancePath.LOGIN_PASSWORD, 1L, 1L,
                        UserTypeEnum.ADMIN.getValue(), "default", null, List.of("pwd")));

        assertThrows(ServiceException.class, () ->
                facade.refresh(MfaIssuancePath.REFRESH_TOKEN, "rt", "default"));
    }

    /** F-01-4 类：enrollment pending refresh REJECT */
    @Test
    void f01_4_enrollmentPendingRefreshRejected() {
        when(userFactorProbe.isEnrollmentPendingOnSession(anyString())).thenReturn(true);
        assertThrows(ServiceException.class, () ->
                facade.refresh(MfaIssuancePath.REFRESH_TOKEN, "rt", "default"));
        verify(oauth2TokenService, never()).refreshAccessToken(anyString(), anyString());
    }

    /** R-01: tokenClass 骨架 */
    @Test
    void r01_tokenClassGuard() {
        assertTrue(MfaTokenClassGuard.allowsBusinessApi(null));
        assertTrue(MfaTokenClassGuard.allowsBusinessApi(MfaTokenClass.ACCESS.name()));
        assertFalse(MfaTokenClassGuard.allowsBusinessApi(MfaTokenClass.PRE_AUTH.name()));
        assertFalse(MfaTokenClassGuard.allowsBusinessApi(MfaTokenClass.ENROLLMENT.name()));
        assertFalse(MfaTokenClassGuard.allowsBusinessApi(MfaTokenClass.RECOVERY.name()));
        assertTrue(MfaTokenClassGuard.isChallengeHandleClass(MfaTokenClass.PRE_AUTH.name()));
    }

    /** 决策一次性消费 */
    @Test
    void issuanceDecisionOneShot() {
        var d = cn.iocoder.yudao.module.system.service.mfa.model.IssuanceDecision.builder()
                .subjectId(1L)
                .mfaSatisfied(true)
                .enrollmentComplete(true)
                .recoveryRequired(false)
                .expiresAt(java.time.Instant.now().plusSeconds(30))
                .build();
        assertTrue(d.tryConsume());
        assertFalse(d.tryConsume());
    }

}
