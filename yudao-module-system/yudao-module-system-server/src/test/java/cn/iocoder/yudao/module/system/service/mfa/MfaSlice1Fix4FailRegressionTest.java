package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaControlTuple;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChecksumUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 1 最终审查 FAIL（81568816）反例回归 F-R4-01～03。
 */
public class MfaSlice1Fix4FailRegressionTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyAuthorityImpl policy;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policy = new MfaPolicyAuthorityImpl(store);
    }

    private static MfaControlStateDO validUninitialized(long epoch) {
        String cs = MfaChecksumUtil.computeControl(
                MfaLifecycleState.UNINITIALIZED.name(), MfaMode.OFF.name(), Set.of(), epoch, 0L);
        return MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(MfaLifecycleState.UNINITIALIZED.name())
                .globalMode(MfaMode.OFF.name())
                .globalAllowedFactors("")
                .globalPolicyEpoch(epoch)
                .globalMinAcceptedEpoch(0L)
                .armedAt(null)
                .checksum(cs)
                .legacyGlobalMerged(false)
                .build();
    }

    @Test
    void fr401_baselineValidUninitialized_usable() {
        store.saveControlState(validUninitialized(5L));
        MfaControlTuple t = policy.readControlTuple();
        assertTrue(t.isUsable());
        assertEquals(MfaLifecycleState.UNINITIALIZED, t.getLifecycleState());
        assertEquals(5L, t.getGlobalPolicyEpoch());
        assertEquals(0L, t.getGlobalMinAcceptedEpoch());
    }

    /** F-R4-01: UNINITIALIZED + min!=0 → closed */
    @Test
    void fr401_uninitializedNonzeroMin_closed() {
        MfaControlStateDO row = validUninitialized(5L);
        row.setGlobalMinAcceptedEpoch(5L);
        row.setChecksum(MfaChecksumUtil.computeControl(
                MfaLifecycleState.UNINITIALIZED.name(), MfaMode.OFF.name(), Set.of(), 5L, 5L));
        store.saveControlState(row);
        MfaControlTuple t = policy.readControlTuple();
        assertFalse(t.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, t.getLifecycleState());
    }

    /** F-R4-01: UNINITIALIZED + armed_at → closed；tenant 写拒绝且字段不变 */
    @Test
    void fr401_uninitializedArmedMarker_closedAndTenantWriteRefused() {
        MfaControlStateDO row = validUninitialized(5L);
        LocalDateTime armed = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        row.setArmedAt(armed);
        // checksum still matches fields used in hash (armed_at not in checksum) — still must close
        store.saveControlState(row);

        MfaControlTuple t = policy.readControlTuple();
        assertFalse(t.isUsable(), "armed_at marker must not be usable OFF");
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, t.getLifecycleState());

        assertThrows(IllegalStateException.class, () ->
                policy.confirmTenantPolicy(9L, MfaMode.OFF, Set.of()));

        MfaControlStateDO after = store.getControlState();
        assertEquals(5L, after.getGlobalPolicyEpoch());
        assertEquals(armed, after.getArmedAt());
        assertEquals(MfaLifecycleState.UNINITIALIZED.name(), after.getLifecycleState());
        assertEquals(MfaMode.OFF.name(), after.getGlobalMode());
        assertNull(store.getTenantPolicy(9L));
    }

    /** F-R4-01: blank mode 不得猜 OFF */
    @Test
    void fr401_blankMode_notGuessedOff() {
        MfaControlStateDO row = validUninitialized(5L);
        row.setGlobalMode("  ");
        row.setChecksum(MfaChecksumUtil.computeControl(
                MfaLifecycleState.UNINITIALIZED.name(), MfaMode.OFF.name(), Set.of(), 5L, 0L));
        store.saveControlState(row);
        assertFalse(policy.readControlTuple().isUsable());
    }

    /** F-R4-01: null lifecycle 不得猜 UNINITIALIZED */
    @Test
    void fr401_nullLifecycle_notGuessedUninitialized() {
        MfaControlStateDO row = validUninitialized(5L);
        row.setLifecycleState(null);
        row.setChecksum(MfaChecksumUtil.computeControl(
                MfaLifecycleState.UNINITIALIZED.name(), MfaMode.OFF.name(), Set.of(), 5L, 0L));
        store.saveControlState(row);
        assertFalse(policy.readControlTuple().isUsable());
    }

    /** F-R4-01: 非法 factor type → closed */
    @Test
    void fr401_invalidFactorType_closed() {
        Set<String> bad = Set.of("MAGIC");
        String cs = MfaChecksumUtil.computeControl(
                MfaLifecycleState.UNINITIALIZED.name(), MfaMode.OFF.name(), bad, 5L, 0L);
        store.saveControlState(MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(MfaLifecycleState.UNINITIALIZED.name())
                .globalMode(MfaMode.OFF.name())
                .globalAllowedFactors("MAGIC")
                .globalPolicyEpoch(5L)
                .globalMinAcceptedEpoch(0L)
                .checksum(cs)
                .build());
        MfaControlTuple t = policy.readControlTuple();
        assertFalse(t.isUsable());
        assertTrue(t.getUnusableReason() != null && t.getUnusableReason().contains("factor"));
    }

    /** F-R4-02/03: 迁移 SQL 含持久消费标记 + 禁止 mode 降级 + seed-only 重签 */
    @Test
    void fr402_fr403_migrationSqlHasMergedFlagAndNoDowngrade() throws Exception {
        Path p = findMigrationSql();
        assertNotNull(p);
        String sql = Files.readString(p);
        assertTrue(sql.contains("legacy_global_merged"), "persistent consume flag required");
        assertTrue(sql.contains("legacy_global_merged = b''1''")
                        || sql.contains("legacy_global_merged = b'1'"),
                "merge must set consume flag");
        assertTrue(sql.contains("IFNULL(c.legacy_global_merged, 0) = 0"),
                "merge only when not consumed");
        assertTrue(sql.contains("REQUIRED") && sql.contains("OPTIONAL")
                        && (sql.contains("AND NOT") || sql.contains("NOT IN")),
                "mode downgrade guard required");
        assertTrue(sql.contains("GROUP_CONCAT") && sql.contains("ORDER BY"));
        // seed-only resign: armed_at IS NULL + epoch 0
        assertTrue(sql.contains("armed_at IS NULL"));
        assertTrue(sql.contains("IFNULL(c.global_policy_epoch, 0) = 0"));
    }

    /** clean empty 路径保持 */
    @Test
    void fr401_cleanEmptyTenantFirstStillWorks() {
        long e = policy.confirmTenantPolicy(1L, MfaMode.REQUIRED, Set.of("TOTP"));
        assertTrue(e >= 1);
        assertTrue(policy.readControlTuple().isUsable());
        assertEquals(Boolean.TRUE, store.getControlState().getLegacyGlobalMerged());
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
