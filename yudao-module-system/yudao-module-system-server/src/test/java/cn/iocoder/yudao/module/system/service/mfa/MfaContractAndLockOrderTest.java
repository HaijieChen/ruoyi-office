package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.service.mfa.contract.MfaAuthLoginResult;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaLockOrder;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaTokenClassGuard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-MFA-v3 §6 锁序骨架 + §7 canonical 契约（切片 1）。
 */
public class MfaContractAndLockOrderTest extends BaseMockitoUnitTest {

    @Test
    void lockOrder_canonicalAndValidation() {
        assertEquals(6, MfaLockOrder.CANONICAL_ORDER.size());
        assertTrue(MfaLockOrder.isValidOrder(List.of(
                MfaLockOrder.Resource.GLOBAL_POLICY,
                MfaLockOrder.Resource.TENANT_POLICY,
                MfaLockOrder.Resource.USER_ASSURANCE)));
        assertFalse(MfaLockOrder.isValidOrder(List.of(
                MfaLockOrder.Resource.USER_ASSURANCE,
                MfaLockOrder.Resource.GLOBAL_POLICY)));
        assertThrows(IllegalStateException.class, () ->
                MfaLockOrder.requireValidOrder(List.of(
                        MfaLockOrder.Resource.TOKEN_FAMILY,
                        MfaLockOrder.Resource.FACTOR)));
    }

    @Test
    void tokenClass_rejectsLegacyAliases() {
        assertNull(MfaTokenClass.parseCanonical("challengeToken"));
        assertNull(MfaTokenClass.parseCanonical("CHALLENGE"));
        assertNull(MfaTokenClass.parseCanonical("preAuthToken"));
        assertNull(MfaTokenClass.parseCanonical("enrollmentToken"));
        assertNull(MfaTokenClass.parseCanonical("recoveryToken"));
        assertEquals(MfaTokenClass.ACCESS, MfaTokenClass.parseCanonical("ACCESS"));
        assertEquals(MfaTokenClass.PRE_AUTH, MfaTokenClass.parseCanonical("PRE_AUTH"));
        assertTrue(MfaTokenClass.allowsBusinessApi(MfaTokenClass.ACCESS));
        assertFalse(MfaTokenClass.allowsBusinessApi(MfaTokenClass.PRE_AUTH));
        assertTrue(MfaTokenClassGuard.allowsBusinessApi(MfaTokenClass.ACCESS.name()));
        assertFalse(MfaTokenClassGuard.allowsBusinessApi(MfaTokenClass.RECOVERY.name()));
    }

    @Test
    void authLoginResult_authenticatedHasTokens_flowHasNoTokens() {
        MfaAuthLoginResult ok = MfaAuthLoginResult.authenticated("a", "r", 3600);
        assertEquals(MfaLoginStatus.AUTHENTICATED, ok.getLoginStatus());
        assertTrue(ok.hasAccessOrRefreshToken());
        assertNull(ok.getFlow());

        MfaAuthLoginResult flow = MfaAuthLoginResult.mfaFlow(
                MfaLoginStatus.MFA_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of("opaque-flow", MfaFlowTokenClass.PRE_AUTH, 300));
        assertFalse(flow.hasAccessOrRefreshToken());
        assertNull(flow.getAccessToken());
        assertNull(flow.getRefreshToken());
        assertEquals(MfaFlowTokenClass.PRE_AUTH, flow.getFlow().getTokenClass());
        assertEquals("opaque-flow", flow.getFlow().getFlowToken());
    }

    @Test
    void authLoginResult_rejectsAuthenticatedAsFlow() {
        assertThrows(IllegalArgumentException.class, () ->
                MfaAuthLoginResult.mfaFlow(MfaLoginStatus.AUTHENTICATED,
                        MfaAuthLoginResult.FlowPayload.of("x", MfaFlowTokenClass.PRE_AUTH, 1)));
    }
}
