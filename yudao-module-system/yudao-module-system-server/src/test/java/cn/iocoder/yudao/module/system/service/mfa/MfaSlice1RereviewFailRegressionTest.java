package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.service.mfa.contract.MfaAuthLoginResult;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaControlTuple;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChecksumUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 1 最终重审 FAIL（70591a529）反例回归 F-R2-01～04。
 */
public class MfaSlice1RereviewFailRegressionTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyAuthorityImpl policy;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policy = new MfaPolicyAuthorityImpl(store);
    }

    /** F-R2-01: 损坏 global 已 CLOSED 时 tenant 写拒绝，control 数据不变 */
    @Test
    void fr201_tenantWriteDoesNotRepairCorruptGlobal() {
        // 先合法 ARMED+REQUIRED
        policy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        MfaControlStateDO before = store.getControlState();
        String modeBefore = before.getGlobalMode();
        String checksumBefore = before.getChecksum();
        Long epochBefore = before.getGlobalPolicyEpoch();

        // 损坏 mode
        store.corruptGlobalMode("NOT_A_MODE");
        policy.invalidateCache();
        MfaControlTuple closed = policy.readControlTuple();
        assertFalse(closed.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, closed.getLifecycleState());

        // tenant 写必须拒绝
        assertThrows(IllegalStateException.class, () ->
                policy.confirmTenantPolicy(9L, MfaMode.REQUIRED, Set.of("TOTP")));

        // control 不得被改写成 ARMED/OFF
        MfaControlStateDO after = store.getControlState();
        assertEquals("NOT_A_MODE", after.getGlobalMode());
        assertEquals(checksumBefore, after.getChecksum());
        assertEquals(epochBefore, after.getGlobalPolicyEpoch());
        assertNotEquals(MfaMode.OFF.name(), after.getGlobalMode());
        assertEquals(modeBefore, "REQUIRED"); // 原合法值被损坏前是 REQUIRED
    }

    /** F-R2-01: clean empty tenant-first 仍可 ARMED */
    @Test
    void fr201_cleanTenantFirstStillArms() {
        long e = policy.confirmTenantPolicy(3L, MfaMode.REQUIRED, Set.of("TOTP"));
        assertTrue(e >= 1);
        MfaControlTuple t = policy.readControlTuple();
        assertTrue(t.isUsable());
        assertEquals(MfaLifecycleState.ARMED, t.getLifecycleState());
    }

    /** F-R2-02: PRE_AUTH TTL &gt; 300 拒绝 */
    @Test
    void fr202_preAuthTtlOverCap_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_REQUIRED,
                        MfaAuthLoginResult.FlowPayload.of("t", MfaFlowTokenClass.PRE_AUTH, 600)));
        // 合法上界
        var ok = MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of("t", MfaFlowTokenClass.PRE_AUTH, 300));
        assertEquals(300, ok.getFlow().getExpiresIn());
    }

    /** F-R2-02: 未知 factor type 拒绝 */
    @Test
    void fr202_unknownFactorType_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                MfaAuthLoginResult.FactorRef.of("1", "MAGIC", "x", null));
        var f = MfaAuthLoginResult.FactorRef.of("1", "totp", "Authenticator", null);
        assertEquals("TOTP", f.getType());
        var flow = MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of("tok", MfaFlowTokenClass.PRE_AUTH, 300,
                        List.of("verify"), List.of(f)));
        assertEquals(1, flow.getFlow().getFactors().size());
    }

    /** F-R2-04: epoch 溢出拒绝写 */
    @Test
    void fr204_epochOverflow_refused() {
        store.saveControlState(MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(MfaLifecycleState.ARMED.name())
                .globalMode(MfaMode.OFF.name())
                .globalAllowedFactors("")
                .globalPolicyEpoch(Long.MAX_VALUE)
                .globalMinAcceptedEpoch(0L)
                .checksum(MfaChecksumUtil.computeControl(
                        MfaLifecycleState.ARMED.name(), MfaMode.OFF.name(), Set.of(),
                        Long.MAX_VALUE, 0L))
                .build());
        assertThrows(IllegalStateException.class, () ->
                policy.confirmGlobalPolicy(MfaMode.OFF, Set.of()));
        // 旧值保留
        assertEquals(Long.MAX_VALUE, store.getControlState().getGlobalPolicyEpoch());
    }

    /** F-R2-04: 负 epoch 读 closed */
    @Test
    void fr204_negativeEpoch_readClosed() {
        store.saveControlState(MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(MfaLifecycleState.ARMED.name())
                .globalMode(MfaMode.OFF.name())
                .globalAllowedFactors("")
                .globalPolicyEpoch(Long.MIN_VALUE)
                .globalMinAcceptedEpoch(Long.MIN_VALUE)
                .checksum(MfaChecksumUtil.computeControl(
                        MfaLifecycleState.ARMED.name(), MfaMode.OFF.name(), Set.of(),
                        Long.MIN_VALUE, Long.MIN_VALUE))
                .build());
        MfaControlTuple t = policy.readControlTuple();
        assertFalse(t.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, t.getLifecycleState());
    }

    /** F-R2-03: 迁移脚本含 legacy global 语义合并与 checksum 重算（F-R3 加强门闩） */
    @Test
    void fr203_migrationMergesLegacyGlobalPolicy() throws Exception {
        Path p = findMigrationSql();
        assertNotNull(p);
        String sql = Files.readString(p);
        assertTrue(sql.contains("system_mfa_global_policy"));
        assertTrue(sql.contains("INNER JOIN `system_mfa_global_policy`")
                || sql.contains("INNER JOIN system_mfa_global_policy")
                || sql.contains("system_mfa_global_policy` g"));
        assertTrue(sql.contains("SHA2("), "must recompute checksum");
        assertTrue(sql.contains("confirmed"));
        assertTrue(sql.contains("GROUP_CONCAT") && sql.contains("ORDER BY"),
                "canonical factor order for Java parity");
        assertTrue(sql.contains("^[0-9a-f]{64}$") || sql.contains("NOT REGEXP"),
                "one-shot consume gate");
        assertFalse(sql.contains("可手工合并"), "must not rely on manual merge comment only");
    }

    private static Path findMigrationSql() {
        Path cwd = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            Path hit = cwd.resolve("sql/mysql/system_mfa_v3_authority.sql");
            if (Files.isRegularFile(hit)) {
                return hit;
            }
            hit = cwd.resolve("oa/sql/mysql/system_mfa_v3_authority.sql");
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
