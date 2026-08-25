package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaControlTuple;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ADR-MFA-v3 §4 策略权威 / 生命周期（切片 1）。
 */
public class MfaPolicyAuthorityTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyAuthorityImpl authority;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        authority = new MfaPolicyAuthorityImpl(store);
    }

    @Test
    void emptyDb_uninitializedOff() {
        MfaControlTuple t = authority.readControlTuple();
        assertTrue(t.isUsable());
        assertEquals(MfaLifecycleState.UNINITIALIZED, t.getLifecycleState());
        assertEquals(MfaMode.OFF, t.getGlobalMode());

        MfaPolicySnapshot s = authority.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.UNINITIALIZED, s.getLifecycleState());
        assertEquals(MfaMode.OFF, s.getMode());
        assertTrue(s.isUsable());
        assertTrue(authority.isReady());
    }

    @Test
    void firstNonOffArms_andExplicitOffStaysArmed() {
        long e1 = authority.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        assertTrue(e1 >= 1);
        MfaPolicySnapshot armed = authority.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.ARMED, armed.getLifecycleState());
        assertEquals(MfaMode.OPTIONAL, armed.getMode());

        authority.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot still = authority.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.ARMED, still.getLifecycleState());
        assertEquals(MfaMode.OFF, still.getMode());
        assertNotEquals(MfaLifecycleState.UNINITIALIZED, still.getLifecycleState());
    }

    @Test
    void armedCorruptMode_degradedClosed() {
        authority.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        store.corruptGlobalMode("REQUIERD");
        authority.invalidateCache();
        MfaPolicySnapshot s = authority.resolveEffectivePolicy(null);
        assertFalse(s.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, s.getLifecycleState());
        assertFalse(authority.isReady());
    }

    @Test
    void readFailure_degradedClosed() {
        authority.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        store.setForceReadFailure(true);
        authority.invalidateCache();
        MfaControlTuple t = authority.readControlTuple();
        assertFalse(t.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, t.getLifecycleState());
    }

    @Test
    void globalOffBeatsTenantRequired() {
        authority.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        authority.confirmTenantPolicy(1L, MfaMode.REQUIRED, Set.of("TOTP"));
        authority.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        // tenant row still exists but global OFF wins
        // need tenant row after OFF for resolve - confirmTenant after OFF advances
        authority.confirmTenantPolicy(1L, MfaMode.REQUIRED, Set.of("TOTP"));
        MfaPolicySnapshot s = authority.resolveEffectivePolicy(1L);
        assertTrue(s.isUsable());
        assertEquals(MfaMode.OFF, s.getMode());
    }

    @Test
    void optionalTenantRequired_effectiveRequired() {
        authority.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP", "SMS"));
        authority.confirmTenantPolicy(9L, MfaMode.REQUIRED, Set.of("TOTP"));
        MfaPolicySnapshot g = authority.resolveEffectivePolicy(null);
        MfaPolicySnapshot t = authority.resolveEffectivePolicy(9L);
        assertEquals(MfaMode.OPTIONAL, g.getMode());
        assertEquals(MfaMode.REQUIRED, t.getMode());
        assertTrue(g.isUsable() && t.isUsable());
    }

    @Test
    void factorIntersection_emptyStaysEmpty() {
        authority.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        authority.confirmTenantPolicy(3L, MfaMode.OPTIONAL, Set.of("EMAIL"));
        MfaPolicySnapshot s = authority.resolveEffectivePolicy(3L);
        assertTrue(s.isUsable());
        assertTrue(s.getAllowedFactors().isEmpty());
    }

    @Test
    void armedMissingTenantRow_closed() {
        authority.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        MfaPolicySnapshot s = authority.resolveEffectivePolicy(42L);
        assertFalse(s.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, s.getLifecycleState());
    }

    @Test
    void epochMonotonic() {
        long e0 = authority.readControlTuple().getGlobalPolicyEpoch();
        long e1 = authority.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        long e2 = authority.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        assertTrue(e1 > e0 || e1 >= 1);
        assertTrue(e2 > e1);
        assertEquals(e2, authority.readControlTuple().getGlobalPolicyEpoch());
    }
}
