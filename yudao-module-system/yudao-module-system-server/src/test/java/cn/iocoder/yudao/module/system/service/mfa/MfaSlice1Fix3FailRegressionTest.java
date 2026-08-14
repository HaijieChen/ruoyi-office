package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaControlTuple;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChecksumUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 1 最终审查 FAIL（e3d3f1f2）反例回归 F-R3-01～03。
 */
public class MfaSlice1Fix3FailRegressionTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyAuthorityImpl policy;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policy = new MfaPolicyAuthorityImpl(store);
    }

    /** F-R3-01: 持久化 UNINITIALIZED + 坏 checksum + 非零 epoch → closed，tenant 写拒绝且不回退 epoch */
    @Test
    void fr301_uninitializedCorruptChecksum_closedAndTenantWriteRefused() {
        store.saveControlState(MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(MfaLifecycleState.UNINITIALIZED.name())
                .globalMode(MfaMode.OFF.name())
                .globalAllowedFactors("")
                .globalPolicyEpoch(7L)
                .globalMinAcceptedEpoch(0L)
                .checksum("bad-not-matching")
                .build());

        MfaControlTuple t = policy.readControlTuple();
        assertFalse(t.isUsable(), "corrupt UNINITIALIZED must not be usable OFF");
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, t.getLifecycleState());
        assertNotEquals(0L, store.getControlState().getGlobalPolicyEpoch());

        assertThrows(IllegalStateException.class, () ->
                policy.confirmTenantPolicy(9L, MfaMode.REQUIRED, Set.of("TOTP")));

        MfaControlStateDO after = store.getControlState();
        assertEquals(7L, after.getGlobalPolicyEpoch());
        assertEquals("bad-not-matching", after.getChecksum());
        assertEquals(MfaLifecycleState.UNINITIALIZED.name(), after.getLifecycleState());
        assertEquals(MfaMode.OFF.name(), after.getGlobalMode());
        assertNull(store.getTenantPolicy(9L), "tenant row must not be created");
    }

    /** F-R3-01: 持久化 UNINITIALIZED 负 epoch → closed，tenant 写拒绝 */
    @Test
    void fr301_uninitializedNegativeEpoch_closedAndTenantWriteRefused() {
        store.saveControlState(MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(MfaLifecycleState.UNINITIALIZED.name())
                .globalMode(MfaMode.OFF.name())
                .globalAllowedFactors("")
                .globalPolicyEpoch(-3L)
                .globalMinAcceptedEpoch(-3L)
                .checksum(MfaChecksumUtil.computeControl(
                        MfaLifecycleState.UNINITIALIZED.name(), MfaMode.OFF.name(), Set.of(), -3L, -3L))
                .build());

        MfaControlTuple t = policy.readControlTuple();
        assertFalse(t.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, t.getLifecycleState());

        assertThrows(IllegalStateException.class, () ->
                policy.confirmTenantPolicy(2L, MfaMode.OPTIONAL, Set.of("SMS")));
        assertEquals(-3L, store.getControlState().getGlobalPolicyEpoch());
        assertNull(store.getTenantPolicy(2L));
    }

    /** F-R3-01: 合法持久化 UNINITIALIZED（checksum 匹配、真实 epoch）可读；tenant-first 从真实 epoch 推进 */
    @Test
    void fr301_validUninitializedPreservesEpochOnTenantWrite() {
        long epoch = 5L;
        String cs = MfaChecksumUtil.computeControl(
                MfaLifecycleState.UNINITIALIZED.name(), MfaMode.OFF.name(), Set.of(), epoch, 0L);
        store.saveControlState(MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(MfaLifecycleState.UNINITIALIZED.name())
                .globalMode(MfaMode.OFF.name())
                .globalAllowedFactors("")
                .globalPolicyEpoch(epoch)
                .globalMinAcceptedEpoch(0L)
                .checksum(cs)
                .build());

        MfaControlTuple t = policy.readControlTuple();
        assertTrue(t.isUsable());
        assertEquals(MfaLifecycleState.UNINITIALIZED, t.getLifecycleState());
        assertEquals(epoch, t.getGlobalPolicyEpoch());

        long next = policy.confirmTenantPolicy(3L, MfaMode.REQUIRED, Set.of("TOTP"));
        assertEquals(epoch + 1, next);
        assertEquals(epoch + 1, store.getControlState().getGlobalPolicyEpoch());
        assertEquals(MfaLifecycleState.ARMED.name(), store.getControlState().getLifecycleState());
        assertNotNull(store.getTenantPolicy(3L));
    }

    /** clean empty 路径保持 */
    @Test
    void fr301_cleanEmptyTenantFirstStillWorks() {
        long e = policy.confirmTenantPolicy(1L, MfaMode.REQUIRED, Set.of("TOTP"));
        assertTrue(e >= 1);
        assertTrue(policy.readControlTuple().isUsable());
    }

    /** F-R3-03: Java canonical 因子序（SMS 在 TOTP 前） */
    @Test
    void fr303_javaCanonicalFactorOrder() {
        String unsortedMaterial = MfaChecksumUtil.computeControl(
                MfaLifecycleState.ARMED.name(), MfaMode.REQUIRED.name(),
                Set.of("TOTP", "SMS"), 7L, 7L);
        String sortedSame = MfaChecksumUtil.computeControl(
                MfaLifecycleState.ARMED.name(), MfaMode.REQUIRED.name(),
                Set.of("SMS", "TOTP"), 7L, 7L);
        assertEquals(unsortedMaterial, sortedSame, "Set order must not affect checksum");
        // 明确期望 material 使用 SMS,TOTP
        assertEquals(
                MfaChecksumUtil.computeControl(
                        MfaLifecycleState.ARMED.name(), MfaMode.REQUIRED.name(),
                        Set.of("SMS", "TOTP"), 7L, 7L),
                unsortedMaterial);
    }

    /** F-R3-02/03 + F-R4-02: 迁移 SQL 持久消费标记 + 因子排序 + 禁止降级 */
    @Test
    void fr302_fr303_migrationSqlGuardsAndSortedFactors() throws Exception {
        Path p = findMigrationSql();
        assertNotNull(p);
        String sql = Files.readString(p);
        assertTrue(sql.contains("system_mfa_global_policy"));
        assertTrue(sql.contains("SHA2("));
        assertTrue(sql.contains("legacy_global_merged"), "persistent consume flag");
        assertTrue(sql.contains("GROUP_CONCAT") && sql.contains("ORDER BY"),
                "must sort factors for canonical checksum");
        assertTrue(sql.contains("OPTIONAL") && sql.contains("REQUIRED")
                        && (sql.contains("NOT IN") || sql.contains("AND NOT") || sql.contains("NOT (")),
                "must refuse stale legacy OFF overwrite of non-OFF control");
        assertTrue(sql.contains("global_policy_epoch")
                        && sql.contains("policy_version"),
                "must compare control epoch vs legacy version");
        assertFalse(sql.contains("可手工合并"));
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
