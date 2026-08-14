package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChallengeHandleStore;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaIssuanceGuard;
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

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_ADMIN_DIRECT_ISSUE_FORBIDDEN;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_TOKEN_ISSUANCE_REJECTED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 切片 1：默认 OFF 兼容与 Facade 基础门闩（完整线性化在切片 2）。
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

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyControlServiceImpl realPolicy;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        realPolicy = new MfaPolicyControlServiceImpl(new MfaPolicyAuthorityImpl(store));
        lenient().when(policyControlService.resolveEffectivePolicy(any())).thenAnswer(inv ->
                realPolicy.resolveEffectivePolicy(inv.getArgument(0)));
        lenient().when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);
        lenient().when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(false);
        lenient().when(userFactorProbe.isUserMfaEnabled(any(), any())).thenReturn(false);
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

    @ParameterizedTest
    @EnumSource(value = MfaIssuancePath.class, names = {
            "LOGIN_PASSWORD", "LOGIN_SMS", "LOGIN_SOCIAL", "REGISTER"
    })
    void off_interactiveAllowed(MfaIssuancePath path) {
        stubCreateIssuesToken();
        // ARMED 后缺租户会 closed：OFF 默认用 null tenant（global only）
        MfaIssuanceResult result = facade.issueAfterPrimaryAuth(
                path, 10L, null, UserTypeEnum.ADMIN.getValue(), "default", null, List.of("pwd"));
        assertEquals(MfaIssuanceOutcome.ALLOWED, result.getOutcome());
        assertTrue(result.hasAccessOrRefreshToken());
        assertEquals(MfaLoginStatus.AUTHENTICATED, result.getLoginStatus());
    }

    @Test
    void requiredChallengeZeroToken() {
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        // ARMED+REQUIRED 且无租户行 → closed；为测试需 MFA 路径，先补租户
        realPolicy.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(true);
        when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);

        MfaIssuanceResult result = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));

        assertEquals(MfaIssuanceOutcome.CHALLENGE, result.getOutcome());
        assertFalse(result.hasAccessOrRefreshToken());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void passwordGrantRejectWhenMfaRequired() {
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        realPolicy.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(true);
        when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);

        ServiceException ex = assertThrows(ServiceException.class, () ->
                facade.issueForOAuthGrant(MfaIssuancePath.OAUTH2_PASSWORD, 10L, 1L,
                        UserTypeEnum.ADMIN.getValue(), "default", null, List.of("pwd")));
        assertEquals(MFA_TOKEN_ISSUANCE_REJECTED.getCode(), ex.getCode());
    }

    @Test
    void clientCredentialsNa() {
        when(oauth2TokenService.createAccessToken(eq(0L), anyInt(), anyString(), any()))
                .thenReturn(tokenFor(0L));
        OAuth2AccessTokenDO token = facade.issueClientCredentials("client", List.of("read"));
        assertNotNull(token.getAccessToken());
    }

    @Test
    void internalAdminCreateRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () ->
                facade.rejectInternalAdminCreate(99L, UserTypeEnum.ADMIN.getValue()));
        assertEquals(MFA_ADMIN_DIRECT_ISSUE_FORBIDDEN.getCode(), ex.getCode());
    }
}
