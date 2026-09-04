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
import org.springframework.test.util.ReflectionTestUtils;

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
 * 切片 1/2：默认 OFF 兼容与 Facade 门闩；challenge 使用 flowToken。
 */
public class MfaTokenIssuanceFacadeTest extends BaseMockitoUnitTest {

    @Mock
    private MfaPolicyControlService policyControlService;
    @Mock
    private OAuth2TokenService oauth2TokenService;
    @Mock
    private MfaUserFactorProbe userFactorProbe;

    @Spy
    private MfaAuthFlowServiceImpl authFlowService = new MfaAuthFlowServiceImpl();
    @Spy
    private MfaFactorServiceImpl factorService = new MfaFactorServiceImpl(authFlowService);

    private InMemoryMfaAuthorityStore store;
    private MfaAssuranceAuthorityImpl assuranceAuthority;
    private MfaPolicyControlServiceImpl realPolicy;

    @InjectMocks
    private MfaTokenIssuanceFacadeImpl facade;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        assuranceAuthority = new MfaAssuranceAuthorityImpl(store);
        realPolicy = new MfaPolicyControlServiceImpl(new MfaPolicyAuthorityImpl(store));
        ReflectionTestUtils.setField(facade, "assuranceAuthority", assuranceAuthority);
        ReflectionTestUtils.setField(facade, "authFlowService", authFlowService);
        ReflectionTestUtils.setField(facade, "factorService", factorService);
        lenient().when(policyControlService.resolveEffectivePolicy(any())).thenAnswer(inv ->
                realPolicy.resolveEffectivePolicy(inv.getArgument(0)));
        lenient().when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);
        lenient().when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(false);
        lenient().when(userFactorProbe.isUserMfaEnabled(any(), any())).thenReturn(false);
        MfaIssuanceGuard.clear();
        authFlowService.clear();
        factorService.clear();
    }

    @AfterEach
    void tearDown() {
        MfaIssuanceGuard.clear();
        authFlowService.clear();
        factorService.clear();
    }

    private OAuth2AccessTokenDO tokenFor(Long userId) {
        OAuth2AccessTokenDO t = new OAuth2AccessTokenDO();
        t.setUserId(userId);
        t.setUserType(UserTypeEnum.ADMIN.getValue());
        t.setTenantId(1L);
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
        MfaIssuanceResult result = facade.issueAfterPrimaryAuth(
                path, 10L, null, UserTypeEnum.ADMIN.getValue(), "default", null, List.of("pwd"));
        assertEquals(MfaIssuanceOutcome.ALLOWED, result.getOutcome());
        assertTrue(result.hasAccessOrRefreshToken());
        assertEquals(MfaLoginStatus.AUTHENTICATED, result.getLoginStatus());
    }

    @Test
    void requiredChallengeZeroToken_usesFlowTokenNotChallengeAlias() {
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        realPolicy.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(true);
        when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);

        MfaIssuanceResult result = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));

        assertEquals(MfaIssuanceOutcome.CHALLENGE, result.getOutcome());
        assertFalse(result.hasAccessOrRefreshToken());
        assertNotNull(result.getLoginResp().getFlow());
        assertNotNull(result.getLoginResp().getFlow().getFlowToken());
        assertEquals("PRE_AUTH", result.getLoginResp().getFlow().getTokenClass());
        assertNull(result.getLoginResp().getAccessToken());
        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

    @Test
    void required_imSilentAllowedWithoutChallenge() {
        stubCreateIssuesToken();
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        realPolicy.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));

        MfaIssuanceResult result = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_IM_SILENT, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("im"));

        assertEquals(MfaIssuanceOutcome.ALLOWED, result.getOutcome());
        assertTrue(result.hasAccessOrRefreshToken());
        assertEquals(MfaLoginStatus.AUTHENTICATED, result.getLoginStatus());
        verify(oauth2TokenService).createAccessToken(eq(10L), eq(UserTypeEnum.ADMIN.getValue()), eq("default"), any());
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
                facade.rejectInternalAdminCreate(1L, UserTypeEnum.ADMIN.getValue()));
        assertEquals(MFA_ADMIN_DIRECT_ISSUE_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void completeChallengeAndIssue_totpSuccess() {
        realPolicy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        realPolicy.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        when(userFactorProbe.hasEligibleActiveFactor(any(), any())).thenReturn(true);
        when(userFactorProbe.isEnrollmentComplete(any(), any())).thenReturn(true);
        stubCreateIssuesToken();

        MfaIssuanceResult challenge = facade.issueAfterPrimaryAuth(
                MfaIssuancePath.LOGIN_PASSWORD, 10L, 1L, UserTypeEnum.ADMIN.getValue(),
                "default", null, List.of("pwd"));
        String flowToken = challenge.getLoginResp().getFlow().getFlowToken();
        // allowlist empty on challenge issue means any registered factor with verify action
        factorService.registerActiveFactor(1L, 10L, "f1", "TOTP", "test-secret");

        String code = currentTotp("test-secret");
        MfaIssuanceResult issued = facade.completeChallengeAndIssue(
                flowToken, "f1", "TOTP", code, "default", List.of("read"));
        assertEquals(MfaIssuanceOutcome.ALLOWED, issued.getOutcome());
        assertTrue(issued.hasAccessOrRefreshToken());
        // one-time consume
        assertThrows(ServiceException.class, () ->
                facade.completeChallengeAndIssue(flowToken, "f1", "TOTP", code, "default", List.of("read")));
    }

    private static String currentTotp(String secret) {
        try {
            long step = java.time.Instant.now().getEpochSecond() / 30L;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
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
