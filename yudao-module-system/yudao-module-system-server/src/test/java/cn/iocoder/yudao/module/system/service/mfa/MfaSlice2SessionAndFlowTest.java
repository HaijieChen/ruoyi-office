package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaAuthFlowState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuedFlow;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_SESSION_REJECTED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_TOKEN_CLASS_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 2：AuthFlow / SessionGuard / 因子 / 锁序（含 fail-closed 元数据）。
 */
public class MfaSlice2SessionAndFlowTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyControlServiceImpl policyControl;
    private MfaAssuranceAuthorityImpl assurance;
    private MfaAuthFlowServiceImpl flowService;
    private MfaFactorServiceImpl factorService;
    private MfaSessionGuardImpl sessionGuard;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policyControl = new MfaPolicyControlServiceImpl(new MfaPolicyAuthorityImpl(store));
        assurance = new MfaAssuranceAuthorityImpl(store);
        flowService = new MfaAuthFlowServiceImpl();
        flowService.clear();
        factorService = new MfaFactorServiceImpl(flowService);
        sessionGuard = new MfaSessionGuardImpl();
        ReflectionTestUtils.setField(sessionGuard, "policyControlService", policyControl);
        ReflectionTestUtils.setField(sessionGuard, "assuranceAuthority", assurance);
    }

    @Test
    void flow_issueResolveCompleteIsOneShot() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot snap = policyControl.resolveEffectivePolicy(null);
        MfaIssuedFlow issued = flowService.issue(
                MfaFlowTokenClass.PRE_AUTH, 9L, 1L, "c", snap, 0L,
                List.of("verify"), List.of("f1"), 300);
        assertNotNull(issued.getFlowToken());
        MfaAuthFlowRecord active = flowService.resolveActive(issued.getFlowToken());
        assertNotNull(active);
        assertEquals(MfaAuthFlowState.ACTIVE, active.getState());
        assertTrue(flowService.tryComplete(issued.getFlowToken()));
        assertFalse(flowService.tryComplete(issued.getFlowToken()));
        assertNull(flowService.resolveActive(issued.getFlowToken()));
    }

    @Test
    void flow_sharedAcrossJvmInstancesInProcess() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot snap = policyControl.resolveEffectivePolicy(null);
        MfaAuthFlowServiceImpl a = new MfaAuthFlowServiceImpl();
        MfaAuthFlowServiceImpl b = new MfaAuthFlowServiceImpl();
        MfaIssuedFlow issued = a.issue(MfaFlowTokenClass.PRE_AUTH, 1L, 1L, "c", snap, 0L,
                List.of(), List.of(), 60);
        assertNotNull(b.resolveActive(issued.getFlowToken()));
    }

    @Test
    void sms_allowlistRejectsOtherFactor() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot snap = policyControl.resolveEffectivePolicy(1L);
        MfaIssuedFlow issued = flowService.issue(
                MfaFlowTokenClass.PRE_AUTH, 3L, 1L, "c", snap, 0L,
                List.of("logout"), List.of("allowed-only"), 300);
        factorService.registerActiveFactor(1L, 3L, "other", "SMS", "masked");
        assertThrows(IllegalStateException.class, () ->
                factorService.sendChallengeCode(issued.getFlowToken(), "other", "SMS"));
    }

    @Test
    void sms_sendAndVerify_thenFailReplay() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot snap = policyControl.resolveEffectivePolicy(1L);
        MfaIssuedFlow issued = flowService.issue(
                MfaFlowTokenClass.PRE_AUTH, 3L, 1L, "c", snap, 0L,
                List.of("send", "verify"), List.of("sms1"), 300);
        factorService.registerActiveFactor(1L, 3L, "sms1", "SMS", "138****0000");
        factorService.sendChallengeCode(issued.getFlowToken(), "sms1", "SMS");
        String code = factorService.peekDeliveryCodeForTest(issued.getFlowToken(), "sms1");
        assertNotNull(code);
        assertTrue(factorService.verifyChallengeCode(issued.getFlowToken(), "sms1", "SMS", code));
        assertFalse(factorService.verifyChallengeCode(issued.getFlowToken(), "sms1", "SMS", code));
    }

    @Test
    void totp_sameStepCannotReplayAcrossFlows() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot snap = policyControl.resolveEffectivePolicy(1L);
        String secret = "totp-secret";
        factorService.registerActiveFactor(1L, 10L, "totp1", "TOTP", secret);
        MfaIssuedFlow first = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 10L, 1L, "c", snap, 0L,
                List.of("verify"), List.of("totp1"), 300);
        MfaIssuedFlow second = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 10L, 1L, "c", snap, 0L,
                List.of("verify"), List.of("totp1"), 300);
        String code = currentTotp(secret);
        assertTrue(factorService.verifyChallengeCode(first.getFlowToken(), "totp1", "TOTP", code));
        assertFalse(factorService.verifyChallengeCode(second.getFlowToken(), "totp1", "TOTP", code));
    }

    @Test
    void sessionGuard_rejectsMissingMetadata() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assurance.ensureBootstrapRow(1L, 10L);
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO();
        token.setUserId(10L);
        token.setUserType(UserTypeEnum.ADMIN.getValue());
        token.setTenantId(1L);
        token.setUserInfo(null);
        ServiceException ex = assertThrows(ServiceException.class, () ->
                sessionGuard.assertAccessAllowed(token));
        assertEquals(MFA_SESSION_REJECTED.getCode(), ex.getCode());
    }

    @Test
    void sessionGuard_rejectsStaleAssuranceEpoch() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assurance.ensureBootstrapRow(1L, 10L);
        OAuth2AccessTokenDO token = validAdminToken(10L, 1L, 0L, 0L, 0L);
        sessionGuard.assertAccessAllowed(token);
        assurance.bumpAssuranceEpoch(1L, 10L, null, null);
        ServiceException ex = assertThrows(ServiceException.class, () ->
                sessionGuard.assertAccessAllowed(token));
        assertEquals(MFA_SESSION_REJECTED.getCode(), ex.getCode());
    }

    @Test
    void sessionGuard_rejectsNonAccessTokenClass() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assurance.ensureBootstrapRow(1L, 10L);
        OAuth2AccessTokenDO token = validAdminToken(10L, 1L, 0L, 0L, 0L);
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TOKEN_CLASS, "PRE_AUTH");
        ServiceException ex = assertThrows(ServiceException.class, () ->
                sessionGuard.assertAccessAllowed(token));
        assertEquals(MFA_TOKEN_CLASS_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void lockOrder_flowIssueUsesCanonicalPrefix() {
        assertThrows(IllegalStateException.class, () ->
                MfaLockOrder.requireValidOrder(List.of(
                        MfaLockOrder.Resource.TOKEN_FAMILY,
                        MfaLockOrder.Resource.GLOBAL_POLICY)));
    }

    private OAuth2AccessTokenDO validAdminToken(Long userId, Long tenantId,
                                                long g, long t, long a) {
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO();
        token.setUserId(userId);
        token.setUserType(UserTypeEnum.ADMIN.getValue());
        token.setTenantId(tenantId);
        token.setUserInfo(new HashMap<>());
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TOKEN_CLASS, MfaTokenClass.ACCESS.name());
        token.getUserInfo().put(MfaSessionGuardImpl.UI_SUBJECT_CLASS, MfaSessionGuardImpl.SUBJECT_ADMIN_USER);
        token.getUserInfo().put(MfaSessionGuardImpl.UI_GLOBAL_EPOCH, String.valueOf(g));
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TENANT_EPOCH, String.valueOf(t));
        token.getUserInfo().put(MfaSessionGuardImpl.UI_ASSURANCE_EPOCH, String.valueOf(a));
        return token;
    }

    private static String currentTotp(String secret) {
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
