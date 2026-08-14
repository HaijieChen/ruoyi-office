package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaEnrollmentTotpConfirmReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaEnrollmentTotpStartReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthMfaEnrollmentTotpStartRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthServiceImpl;
import cn.iocoder.yudao.module.system.service.mfa.crypto.MfaSecretCipherImpl;
import cn.iocoder.yudao.module.system.service.mfa.crypto.MfaSecretProperties;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorBinding;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuanceResult;
import cn.iocoder.yudao.module.system.service.mfa.store.InMemoryMfaEnrollSagaStore;
import cn.iocoder.yudao.module.system.service.mfa.store.InMemoryMfaFactorStore;
import cn.iocoder.yudao.module.system.service.mfa.store.MfaEnrollSagaStore;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 关闭 F-S4-FINAL-01～03（终审 @ 0c70e964 不得转签）。
 */
public class MfaSlice4FinalFailRegressionTest extends BaseMockitoUnitTest {

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
        assurance = spy(new MfaAssuranceAuthorityImpl(store));
        flowService.clear();
        factorService.clear();
        InMemoryMfaEnrollSagaStore.shared().clear();
        facade = new MfaTokenIssuanceFacadeImpl();
        MfaEnrollmentCommitter committer = new MfaEnrollmentCommitter(factorService, flowService, assurance);
        ReflectionTestUtils.setField(facade, "policyControlService", policyControl);
        ReflectionTestUtils.setField(facade, "authFlowService", flowService);
        ReflectionTestUtils.setField(facade, "factorService", factorService);
        ReflectionTestUtils.setField(facade, "assuranceAuthority", assurance);
        ReflectionTestUtils.setField(facade, "enrollmentCommitter", committer);
        ReflectionTestUtils.setField(facade, "enrollSagaStore", InMemoryMfaEnrollSagaStore.shared());
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
        InMemoryMfaEnrollSagaStore.shared().clear();
    }

    @Test
    void fs401_assuranceBumpFailure_rollsBackFactorAndFlow() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        String flowToken = startEnrollment(10L);
        AuthMfaEnrollmentTotpStartRespVO start = startTotp(flowToken, 10L);
        doThrow(new RuntimeException("bump fail")).when(assurance)
                .bumpAssuranceEpoch(any(), any(), any(), any());

        AuthMfaEnrollmentTotpConfirmReqVO confirm = confirmReq(flowToken, start, totp(start.getSecretManual()));
        assertThrows(RuntimeException.class, () -> authService.mfaEnrollmentTotpConfirm(confirm));

        assertEquals("PENDING", factorService.peekFactorStatus(1L, 10L, start.getFactorId()));
        assertNotNull(flowService.resolveActive(flowToken));
        assertEquals(0L, assurance.getAssurance(1L, 10L).getAssuranceEpoch());
        assertEquals("NONE", assurance.getAssurance(1L, 10L).getEnrollmentState().name());
        verify(oauth2TokenService, atLeastOnce()).removeAccessToken(anyString());
    }

    @Test
    void fs401_revokeFailure_persistsCompensateThenRetry() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        String flowToken = startEnrollment(10L);
        AuthMfaEnrollmentTotpStartRespVO start = startTotp(flowToken, 10L);
        doThrow(new RuntimeException("bump fail")).doCallRealMethod().when(assurance)
                .bumpAssuranceEpoch(any(), any(), any(), any());
        doThrow(new RuntimeException("revoke fail")).doReturn(null).when(oauth2TokenService)
                .removeAccessToken(anyString());

        AuthMfaEnrollmentTotpConfirmReqVO confirm = confirmReq(flowToken, start, totp(start.getSecretManual()));
        assertThrows(RuntimeException.class, () -> authService.mfaEnrollmentTotpConfirm(confirm));
        String hash = MfaAuthFlowServiceImpl.sha256Hex(flowToken);
        assertEquals(MfaEnrollSagaStore.COMPENSATE_TOKEN, InMemoryMfaEnrollSagaStore.shared().get(hash).state());
        assertEquals("PENDING", factorService.peekFactorStatus(1L, 10L, start.getFactorId()));

        AuthLoginRespVO loggedIn = authService.mfaEnrollmentTotpConfirm(confirm);
        assertEquals("AUTHENTICATED", loggedIn.getLoginStatus());
        assertEquals(MfaEnrollSagaStore.COMMITTED, InMemoryMfaEnrollSagaStore.shared().get(hash).state());
    }

    @Test
    void fs402_cipher_aesGcm_roundTripAndRejectPlainDev() {
        MfaSecretCipherImpl cipher = MfaSecretCipherImpl.forTests("k1");
        String ct = cipher.encrypt("super-secret");
        assertNotEquals("super-secret", ct);
        assertEquals("super-secret", cipher.decrypt("k1", ct));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("plain-dev", ct));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("missing", ct));
        assertThrows(Exception.class, () -> new MfaSecretCipherImpl("plain-dev", Map.of("plain-dev", new byte[32])));
        MfaSecretProperties empty = new MfaSecretProperties();
        assertThrows(IllegalStateException.class, () -> MfaSecretCipherImpl.fromProperties(empty));
    }

    @Test
    void fs403_factorKey_rejectsBlank_andSqlHasUniqueBackfill() throws Exception {
        InMemoryMfaFactorStore s = InMemoryMfaFactorStore.shared();
        s.clear();
        assertThrows(IllegalArgumentException.class, () -> s.save(1L, 1L, MfaFactorBinding.builder()
                .factorId(" ")
                .type("TOTP")
                .status("PENDING")
                .secretOrDestination("x")
                .lastUsedStep(-1L)
                .build()));
        Path sql = findFix1Sql();
        assertNotNull(sql);
        String text = Files.readString(sql);
        assertTrue(text.contains("CONCAT('legacy-', `id`)"));
        assertTrue(text.contains("uk_mfa_factor_tenant_user_key"));
        assertTrue(text.contains("UNIQUE KEY"));
        assertFalse(text.contains("SET `factor_key` = ''"));
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

    private static Path findFix1Sql() {
        Path cwd = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            Path hit = cwd.resolve("sql/mysql/system_mfa_v3_slice4_fix1.sql");
            if (Files.isRegularFile(hit)) {
                return hit;
            }
            hit = cwd.resolve("oa/sql/mysql/system_mfa_v3_slice4_fix1.sql");
            if (Files.isRegularFile(hit)) {
                return hit;
            }
            cwd = cwd.getParent();
            if (cwd == null) {
                break;
            }
        }
        return null;
    }
}
