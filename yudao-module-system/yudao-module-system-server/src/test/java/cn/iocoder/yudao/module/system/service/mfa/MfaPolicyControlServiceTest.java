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
 * F-03 验收反例 1–8 中可自动化部分（①）。
 */
public class MfaPolicyControlServiceTest extends BaseMockitoUnitTest {

    private InMemoryMfaPolicyStore store;
    private MfaPolicyControlServiceImpl service;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaPolicyStore();
        service = new MfaPolicyControlServiceImpl(store);
    }

    /** F-03-1: 空库 UNINITIALIZED/OFF；首个非 OFF 变 ARMED；改回 OFF 不能回 UNINITIALIZED */
    @Test
    void f03_1_uninitializedThenArmMonotonic() {
        MfaPolicySnapshot empty = service.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.UNINITIALIZED, empty.getLifecycleState());
        assertEquals(MfaMode.OFF, empty.getMode());
        assertTrue(empty.isUsable());

        long v1 = service.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        assertTrue(v1 >= 1);
        MfaPolicySnapshot armed = service.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.ARMED, armed.getLifecycleState());
        assertEquals(MfaMode.OPTIONAL, armed.getMode());

        service.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot stillArmed = service.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.ARMED, stillArmed.getLifecycleState());
        assertEquals(MfaMode.OFF, stillArmed.getMode());
        assertTrue(stillArmed.isUsable());
        assertNotEquals(MfaLifecycleState.UNINITIALIZED, stillArmed.getLifecycleState());
    }

    /** F-03-2: ARMED 下策略缺失/非法/checksum → closed，不可签发 */
    @Test
    void f03_2_armedCorruptBecomesDegradedClosed() {
        service.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        assertTrue(service.resolveEffectivePolicy(null).isUsable());

        store.corruptGlobalMode("REQUIERD"); // 非法 enum
        service.invalidateCache();
        MfaPolicySnapshot closed = service.resolveEffectivePolicy(null);
        assertFalse(closed.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, closed.getLifecycleState());
    }

    @Test
    void f03_2_armedMissingRowClosed() {
        service.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        store.removeGlobalPolicyRow();
        service.invalidateCache();
        MfaPolicySnapshot closed = service.resolveEffectivePolicy(null);
        assertFalse(closed.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, closed.getLifecycleState());
    }

    @Test
    void f03_2_checksumMismatchClosed() {
        service.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP", "SMS"));
        store.getGlobalPolicy().setChecksum("deadbeef");
        service.invalidateCache();
        MfaPolicySnapshot closed = service.resolveEffectivePolicy(null);
        assertFalse(closed.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, closed.getLifecycleState());
    }

    /** F-03-3: ARMED+合法 OFF 可登录语义；随后破坏配置 → closed，不因最后值为 OFF fail-open */
    @Test
    void f03_3_explicitOffThenCorruptCloses() {
        service.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        service.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        MfaPolicySnapshot off = service.resolveEffectivePolicy(null);
        assertTrue(off.isUsable());
        assertEquals(MfaMode.OFF, off.getMode());
        assertEquals(MfaLifecycleState.ARMED, off.getLifecycleState());

        store.corruptGlobalMode(null);
        service.invalidateCache();
        MfaPolicySnapshot closed = service.resolveEffectivePolicy(null);
        assertFalse(closed.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, closed.getLifecycleState());
    }

    /** F-03-4: 冷启动阻断读取 → readiness 失败 */
    @Test
    void f03_4_coldStartReadFailureNotReady() {
        service.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        store.setForceReadFailure(true);
        service.invalidateCache();
        assertFalse(service.isReady());
        MfaPolicySnapshot snap = service.resolveEffectivePolicy(null);
        assertFalse(snap.isUsable());
    }

    /** F-03-5: 缓存版本落后于门闩不得使用旧缓存 */
    @Test
    void f03_5_staleCacheMustReload() {
        long v1 = service.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        MfaPolicySnapshot s1 = service.resolveEffectivePolicy(null);
        assertEquals(v1, s1.getPolicyVersion());

        long v2 = service.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        assertTrue(v2 > v1);
        MfaPolicySnapshot s2 = service.resolveEffectivePolicy(null);
        assertEquals(v2, s2.getPolicyVersion());
        assertEquals(MfaMode.REQUIRED, s2.getMode());
        assertNotEquals(s1.getChecksum(), s2.getChecksum());
    }

    /** F-03-7 关联：DB 超时在 ARMED 后 closed */
    @Test
    void f03_timeoutAfterArmedClosed() {
        service.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        store.setForceReadFailure(true);
        service.invalidateCache();
        MfaPolicySnapshot snap = service.resolveEffectivePolicy(null);
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, snap.getLifecycleState());
        assertFalse(snap.isUsable());
    }

    @Test
    void parseFailureIsNotOff() {
        assertNull(MfaMode.parseStrict("REQUIERD"));
        assertNull(MfaMode.parseStrict(""));
        assertNull(MfaMode.parseStrict(null));
        assertEquals(MfaMode.OFF, MfaMode.parseStrict("off"));
    }

    @Test
    void primaryGateMonotonic() {
        assertEquals(0L, service.readPrimaryPolicyVersionGate());
        long v1 = service.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        // 显式 OFF 在 UNINITIALIZED 仍可递增版本
        assertTrue(service.readPrimaryPolicyVersionGate() >= v1 || v1 == 0);
        long v2 = service.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        assertEquals(v2, service.readPrimaryPolicyVersionGate());
        long v3 = service.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assertTrue(v3 > v2);
        assertEquals(MfaLifecycleState.ARMED, service.resolveEffectivePolicy(null).getLifecycleState());
    }

}
