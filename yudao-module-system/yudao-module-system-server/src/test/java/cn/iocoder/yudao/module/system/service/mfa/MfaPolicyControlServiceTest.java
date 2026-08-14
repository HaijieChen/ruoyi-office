package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 兼容适配器测试（委托 PolicyAuthority）。
 */
public class MfaPolicyControlServiceTest extends BaseMockitoUnitTest {

    private MfaPolicyControlServiceImpl service;

    @BeforeEach
    void setUp() {
        InMemoryMfaAuthorityStore store = new InMemoryMfaAuthorityStore();
        service = new MfaPolicyControlServiceImpl(new MfaPolicyAuthorityImpl(store));
    }

    @Test
    void uninitializedThenArm() {
        MfaPolicySnapshot empty = service.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.UNINITIALIZED, empty.getLifecycleState());
        assertEquals(MfaMode.OFF, empty.getMode());

        service.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        MfaPolicySnapshot armed = service.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.ARMED, armed.getLifecycleState());

        service.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assertEquals(MfaLifecycleState.ARMED, service.resolveEffectivePolicy(null).getLifecycleState());
    }

    @Test
    void primaryGateMonotonic() {
        long v1 = service.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        long v2 = service.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        assertTrue(v2 > v1);
        assertEquals(v2, service.readPrimaryPolicyVersionGate());
    }
}
