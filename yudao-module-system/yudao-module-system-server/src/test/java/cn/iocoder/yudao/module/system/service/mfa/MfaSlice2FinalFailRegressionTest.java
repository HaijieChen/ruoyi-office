package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthLoginRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuanceOutcome;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaIssuancePath;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuedFlow;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_SESSION_REJECTED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_TOKEN_ISSUANCE_REJECTED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 2 最终 FAIL（91b1999d）反例回归 F-S2-01～06。
 */
public class MfaSlice2FinalFailRegressionTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyControlServiceImpl policyControl;
    private MfaAssuranceAuthorityImpl assurance;
    private MfaAuthFlowServiceImpl flowService;
    private MfaFactorServiceImpl factors;
    private MfaSessionGuardImpl guard;
    private MfaTokenIssuanceFacadeImpl facade;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policyControl = new MfaPolicyControlServiceImpl(new MfaPolicyAuthorityImpl(store));
        assurance = new MfaAssuranceAuthorityImpl(store);
        flowService = new MfaAuthFlowServiceImpl();
        flowService.clear();
        factors = new MfaFactorServiceImpl(flowService);
        guard = new MfaSessionGuardImpl();
        ReflectionTestUtils.setField(guard, "policyControlService", policyControl);
        ReflectionTestUtils.setField(guard, "assuranceAuthority", assurance);
        facade = new MfaTokenIssuanceFacadeImpl();
        ReflectionTestUtils.setField(facade, "policyControlService", policyControl);
        ReflectionTestUtils.setField(facade, "authFlowService", flowService);
        ReflectionTestUtils.setField(facade, "factorService", factors);
        ReflectionTestUtils.setField(facade, "assuranceAuthority", assurance);
        ReflectionTestUtils.setField(facade, "userFactorProbe", new MfaUserFactorProbe.Noop());
        ReflectionTestUtils.setField(facade, "oauth2TokenService", tokenService(false));
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assurance.ensureBootstrapRow(1L, 10L);
    }

    /** F-S2-02: 缺元数据 fail-closed */
    @Test
    void fs201_guardMissingMetadata_rejected() {
        OAuth2AccessTokenDO legacy = new OAuth2AccessTokenDO();
        legacy.setUserId(10L);
        legacy.setUserType(UserTypeEnum.ADMIN.getValue());
        legacy.setTenantId(1L);
        legacy.setUserInfo(null);
        ServiceException ex = assertThrows(ServiceException.class, () ->
                guard.assertAccessAllowed(legacy));
        assertEquals(MFA_SESSION_REJECTED.getCode(), ex.getCode());
    }

    /** F-S2-01: Token 插入失败后 flow 仍 ACTIVE */
    @Test
    void fs201_tokenInsertFailure_flowRemainsActive() {
        MfaPolicySnapshot policy = policyControl.resolveEffectivePolicy(1L);
        MfaIssuedFlow failureFlow = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 10L, 1L, "c", policy,
                0L, List.of("verify", "send"), List.of("sms-fail"), 300);
        factors.registerActiveFactor(1L, 10L, "sms-fail", "SMS", "masked");
        factors.sendChallengeCode(failureFlow.getFlowToken(), "sms-fail", "SMS");
        String code = factors.peekDeliveryCodeForTest(failureFlow.getFlowToken(), "sms-fail");
        ReflectionTestUtils.setField(facade, "oauth2TokenService", tokenService(true));
        assertThrows(RuntimeException.class, () ->
                facade.completeChallengeAndIssue(failureFlow.getFlowToken(), "sms-fail", "SMS",
                        code, "c", List.of("read")));
        assertNotNull(flowService.resolveActive(failureFlow.getFlowToken()),
                "flow must remain ACTIVE after token failure");
    }

    /** F-S2-04: allowlist 拒绝非允许 factor */
    @Test
    void fs204_disallowedFactor_rejected() {
        MfaPolicySnapshot policy = policyControl.resolveEffectivePolicy(1L);
        MfaIssuedFlow allowlisted = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 10L, 1L, "c", policy,
                0L, List.of("logout"), List.of("allowed-only"), 300);
        factors.registerActiveFactor(1L, 10L, "other", "SMS", "masked");
        assertThrows(IllegalStateException.class, () ->
                factors.sendChallengeCode(allowlisted.getFlowToken(), "other", "SMS"));
    }

    /** F-S2-04: TOTP 同 step 不可跨 flow */
    @Test
    void fs204_totpReplayAcrossFlows_rejected() {
        MfaPolicySnapshot policy = policyControl.resolveEffectivePolicy(1L);
        String secret = "final-review-secret";
        factors.registerActiveFactor(1L, 10L, "totp1", "TOTP", secret);
        MfaIssuedFlow first = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 10L, 1L, "c", policy,
                0L, List.of("verify"), List.of("totp1"), 300);
        MfaIssuedFlow second = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 10L, 1L, "c", policy,
                0L, List.of("verify"), List.of("totp1"), 300);
        String totp = hotp(secret, java.time.Instant.now().getEpochSecond() / 30L);
        assertTrue(factors.verifyChallengeCode(first.getFlowToken(), "totp1", "TOTP", totp));
        assertFalse(factors.verifyChallengeCode(second.getFlowToken(), "totp1", "TOTP", totp));
    }

    /** F-S2-04: assurance bump 后旧 flow 拒绝签发 */
    @Test
    void fs204_staleAssuranceFlow_rejected() {
        MfaPolicySnapshot policy = policyControl.resolveEffectivePolicy(1L);
        long a0 = assurance.getAssurance(1L, 10L).getAssuranceEpoch();
        MfaIssuedFlow stale = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 10L, 1L, "c", policy,
                a0, List.of("verify", "send"), List.of("sms-stale"), 300);
        factors.registerActiveFactor(1L, 10L, "sms-stale", "SMS", "masked");
        factors.sendChallengeCode(stale.getFlowToken(), "sms-stale", "SMS");
        String code = factors.peekDeliveryCodeForTest(stale.getFlowToken(), "sms-stale");
        assurance.bumpAssuranceEpoch(1L, 10L, null, null);
        ServiceException ex = assertThrows(ServiceException.class, () ->
                facade.completeChallengeAndIssue(stale.getFlowToken(), "sms-stale", "SMS",
                        code, "c", List.of("read")));
        assertEquals(MFA_TOKEN_ISSUANCE_REJECTED.getCode(), ex.getCode());
    }

    /** F-S2-03: REQUIRED 下无 session epoch 的 refresh 拒绝 */
    @Test
    void fs203_requiredStaleRefresh_rejected() {
        policyControl.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        policyControl.confirmTenantPolicy(1L, MfaMode.INHERIT, Set.of("TOTP"));
        ServiceException ex = assertThrows(ServiceException.class, () ->
                facade.refresh(MfaIssuancePath.REFRESH_TOKEN, "stale-refresh-without-epochs", "c"));
        assertEquals(MFA_TOKEN_ISSUANCE_REJECTED.getCode(), ex.getCode());
    }

    /** F-S2-05: JSON 为 nested flow union */
    @Test
    void fs205_authResponseJson_nestedFlowUnion() {
        AuthLoginRespVO vo = AuthLoginRespVO.builder()
                .loginStatus("MFA_REQUIRED")
                .flow(AuthLoginRespVO.FlowPayload.builder()
                        .flowToken("opaque")
                        .tokenClass("PRE_AUTH")
                        .expiresIn(300)
                        .allowedActions(List.of("verify"))
                        .build())
                .build();
        String json = JsonUtils.toJsonString(vo);
        assertTrue(json.contains("\"flow\""));
        assertTrue(json.contains("\"flowToken\""));
        assertFalse(json.contains("challengeToken"));
        // 顶层不应平铺 tokenClass（在 flow 内）
        assertTrue(json.contains("\"tokenClass\":\"PRE_AUTH\"")
                || json.contains("\"tokenClass\": \"PRE_AUTH\""));
        // 无 accessToken 字段或为 null 省略
        assertFalse(json.contains("\"accessToken\":\""));
    }

    /** F-S2: happy path complete 仍可用 */
    @Test
    void fs2_completeChallenge_happyPath() {
        MfaPolicySnapshot policy = policyControl.resolveEffectivePolicy(1L);
        long a0 = assurance.getAssurance(1L, 10L).getAssuranceEpoch();
        MfaIssuedFlow flow = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 10L, 1L, "c", policy,
                a0, List.of("verify", "send"), List.of("sms-ok"), 300);
        factors.registerActiveFactor(1L, 10L, "sms-ok", "SMS", "m");
        factors.sendChallengeCode(flow.getFlowToken(), "sms-ok", "SMS");
        String code = factors.peekDeliveryCodeForTest(flow.getFlowToken(), "sms-ok");
        var result = facade.completeChallengeAndIssue(flow.getFlowToken(), "sms-ok", "SMS",
                code, "c", List.of("read"));
        assertEquals(MfaIssuanceOutcome.ALLOWED, result.getOutcome());
        assertNull(flowService.resolveActive(flow.getFlowToken()));
    }

    private static OAuth2TokenService tokenService(boolean failCreate) {
        return (OAuth2TokenService) Proxy.newProxyInstance(
                OAuth2TokenService.class.getClassLoader(),
                new Class<?>[]{OAuth2TokenService.class},
                (proxy, method, args) -> {
                    if ("createAccessToken".equals(method.getName())) {
                        if (failCreate) {
                            throw new IllegalStateException("forced token insert failure");
                        }
                        return adminToken();
                    }
                    if ("refreshAccessToken".equals(method.getName())) {
                        return adminToken();
                    }
                    if ("removeAccessToken".equals(method.getName())) {
                        return null;
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    return null;
                });
    }

    private static OAuth2AccessTokenDO adminToken() {
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO();
        token.setUserId(10L);
        token.setUserType(UserTypeEnum.ADMIN.getValue());
        token.setTenantId(1L);
        token.setAccessToken("access");
        token.setRefreshToken("refresh");
        return token;
    }

    private static String hotp(String secret, long counter) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(counter).array());
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
