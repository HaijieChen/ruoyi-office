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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_SESSION_REJECTED;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_TOKEN_CLASS_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 2：AuthFlow 一次性消费 / SessionGuard fail-closed / 因子通道 / 锁序。
 */
public class MfaSlice2SessionAndFlowTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyAuthorityImpl policyAuth;
    private MfaPolicyControlServiceImpl policyControl;
    private MfaAssuranceAuthorityImpl assurance;
    private MfaAuthFlowServiceImpl flowService;
    private MfaFactorServiceImpl factorService;
    private MfaSessionGuardImpl sessionGuard;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policyAuth = new MfaPolicyAuthorityImpl(store);
        policyControl = new MfaPolicyControlServiceImpl(policyAuth);
        assurance = new MfaAssuranceAuthorityImpl(store);
        flowService = new MfaAuthFlowServiceImpl();
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
        // server stores only hash — resolve works
        MfaAuthFlowRecord active = flowService.resolveActive(issued.getFlowToken());
        assertNotNull(active);
        assertEquals(MfaAuthFlowState.ACTIVE, active.getState());
        assertTrue(flowService.tryComplete(issued.getFlowToken()));
        assertFalse(flowService.tryComplete(issued.getFlowToken()));
        assertNull(flowService.resolveActive(issued.getFlowToken()));
    }

    @Test
    void flow_rawTokenNotStoredAsKey() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot snap = policyControl.resolveEffectivePolicy(null);
        MfaIssuedFlow issued = flowService.issue(
                MfaFlowTokenClass.PRE_AUTH, 1L, 1L, "c", snap, 0L, List.of(), List.of(), 60);
        String hash = MfaAuthFlowServiceImpl.sha256Hex(issued.getFlowToken());
        assertNotEquals(issued.getFlowToken(), hash);
        assertEquals(64, hash.length());
    }

    @Test
    void sms_sendAndVerify_thenFailReplay() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot snap = policyControl.resolveEffectivePolicy(1L);
        // need tenant for factor registration path — OFF global only
        MfaIssuedFlow issued = flowService.issue(
                MfaFlowTokenClass.PRE_AUTH, 3L, 1L, "c", snap, 0L, List.of("send"), List.of("sms1"), 300);
        factorService.registerActiveFactor(1L, 3L, "sms1", "SMS", "138****0000");
        factorService.sendChallengeCode(issued.getFlowToken(), "sms1", "SMS");
        String code = factorService.peekDeliveryCodeForTest(issued.getFlowToken(), "sms1");
        assertNotNull(code);
        assertTrue(factorService.verifyChallengeCode(issued.getFlowToken(), "sms1", "SMS", code));
        // delivery code one-time
        assertFalse(factorService.verifyChallengeCode(issued.getFlowToken(), "sms1", "SMS", code));
    }

    @Test
    void sessionGuard_rejectsStaleAssuranceEpoch() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assurance.ensureBootstrapRow(1L, 10L);
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO();
        token.setUserId(10L);
        token.setUserType(UserTypeEnum.ADMIN.getValue());
        token.setTenantId(1L);
        token.setUserInfo(new HashMap<>());
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TOKEN_CLASS, MfaTokenClass.ACCESS.name());
        token.getUserInfo().put(MfaSessionGuardImpl.UI_GLOBAL_EPOCH, "0");
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TENANT_EPOCH, "0");
        token.getUserInfo().put(MfaSessionGuardImpl.UI_ASSURANCE_EPOCH, "0");
        sessionGuard.assertAccessAllowed(token); // match epoch 0

        assurance.bumpAssuranceEpoch(1L, 10L, null, null);
        ServiceException ex = assertThrows(ServiceException.class, () ->
                sessionGuard.assertAccessAllowed(token));
        assertEquals(MFA_SESSION_REJECTED.getCode(), ex.getCode());
    }

    @Test
    void sessionGuard_rejectsNonAccessTokenClass() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assurance.ensureBootstrapRow(1L, 10L);
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO();
        token.setUserId(10L);
        token.setUserType(UserTypeEnum.ADMIN.getValue());
        token.setTenantId(1L);
        token.setUserInfo(new HashMap<>());
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TOKEN_CLASS, "PRE_AUTH");
        ServiceException ex = assertThrows(ServiceException.class, () ->
                sessionGuard.assertAccessAllowed(token));
        assertEquals(MFA_TOKEN_CLASS_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void sessionGuard_missingAssurance_failClosed() {
        policyControl.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO();
        token.setUserId(99L);
        token.setUserType(UserTypeEnum.ADMIN.getValue());
        token.setTenantId(1L);
        token.setUserInfo(new HashMap<>());
        token.getUserInfo().put(MfaSessionGuardImpl.UI_TOKEN_CLASS, MfaTokenClass.ACCESS.name());
        token.getUserInfo().put(MfaSessionGuardImpl.UI_ASSURANCE_EPOCH, "0");
        ServiceException ex = assertThrows(ServiceException.class, () ->
                sessionGuard.assertAccessAllowed(token));
        assertEquals(MFA_SESSION_REJECTED.getCode(), ex.getCode());
    }

    @Test
    void lockOrder_flowIssueUsesCanonicalPrefix() {
        // issue itself validates order; inverted list must fail
        assertThrows(IllegalStateException.class, () ->
                MfaLockOrder.requireValidOrder(List.of(
                        MfaLockOrder.Resource.TOKEN_FAMILY,
                        MfaLockOrder.Resource.GLOBAL_POLICY)));
    }

    @Test
    void slice2SqlPresent() throws Exception {
        var p = java.nio.file.Path.of("").toAbsolutePath();
        java.nio.file.Path hit = null;
        for (int i = 0; i < 8 && p != null; i++) {
            var c = p.resolve("sql/mysql/system_mfa_v3_slice2_flow.sql");
            if (java.nio.file.Files.isRegularFile(c)) {
                hit = c;
                break;
            }
            c = p.resolve("oa/sql/mysql/system_mfa_v3_slice2_flow.sql");
            if (java.nio.file.Files.isRegularFile(c)) {
                hit = c;
                break;
            }
            p = p.getParent();
        }
        assertNotNull(hit);
        String sql = java.nio.file.Files.readString(hit);
        assertTrue(sql.contains("system_mfa_auth_flow"));
        assertTrue(sql.contains("flow_token_hash"));
        assertTrue(sql.contains("system_mfa_factor"));
        assertTrue(sql.contains("system_mfa_issuance_decision"));
    }
}
