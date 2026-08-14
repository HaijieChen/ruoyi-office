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
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 1 最终审查 FAIL（1344ab73）反例回归 F-R5-01～03。
 */
public class MfaSlice1Fix5FailRegressionTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyAuthorityImpl policy;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policy = new MfaPolicyAuthorityImpl(store);
    }

    /**
     * F-R5-01: 损坏 control（null lifecycle + armed_at）+ confirmGlobalPolicy(OFF)
     * 不得回落 UNINITIALIZED / 清 armed_at；应写 ARMED+OFF 并保留标记。
     */
    @Test
    void fr501_globalOffRepairPreservesArmedMarker() {
        LocalDateTime armed = LocalDateTime.of(2026, 3, 4, 5, 6, 7);
        store.saveControlState(MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(null)
                .globalMode(MfaMode.OFF.name())
                .globalAllowedFactors("")
                .globalPolicyEpoch(5L)
                .globalMinAcceptedEpoch(0L)
                .armedAt(armed)
                .checksum("broken")
                .legacyGlobalMerged(false)
                .build());

        assertFalse(policy.readControlTuple().isUsable());

        long next = policy.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assertEquals(6L, next);

        MfaControlStateDO after = store.getControlState();
        assertEquals(MfaLifecycleState.ARMED.name(), after.getLifecycleState());
        assertEquals(MfaMode.OFF.name(), after.getGlobalMode());
        assertEquals(armed, after.getArmedAt(), "armed_at must not be cleared");
        assertEquals(Boolean.TRUE, after.getLegacyGlobalMerged());

        MfaControlTuple t = policy.readControlTuple();
        assertTrue(t.isUsable());
        assertEquals(MfaLifecycleState.ARMED, t.getLifecycleState());
        assertEquals(MfaMode.OFF, t.getGlobalMode());
    }

    /** F-R5-01: 合法 ARMED 后 confirm OFF 保持 ARMED */
    @Test
    void fr501_armedThenOffStaysArmed() {
        policy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("TOTP"));
        LocalDateTime armed = store.getControlState().getArmedAt();
        assertNotNull(armed);
        policy.confirmGlobalPolicy(MfaMode.OFF, Set.of());
        assertEquals(MfaLifecycleState.ARMED.name(), store.getControlState().getLifecycleState());
        assertEquals(armed, store.getControlState().getArmedAt());
        assertTrue(policy.readControlTuple().isUsable());
        assertEquals(MfaMode.OFF, policy.readControlTuple().getGlobalMode());
    }

    /** F-R5-03: global confirm 拒绝 MAGIC，control 不变 */
    @Test
    void fr503_globalWriteRejectsInvalidFactor() {
        assertThrows(IllegalArgumentException.class, () ->
                policy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("MAGIC")));
        assertNull(store.getControlState());
    }

    /** F-R5-03: tenant confirm 拒绝 MAGIC，tenant/control 不落库 */
    @Test
    void fr503_tenantWriteRejectsInvalidFactor() {
        assertThrows(IllegalArgumentException.class, () ->
                policy.confirmTenantPolicy(9L, MfaMode.REQUIRED, Set.of("MAGIC")));
        assertNull(store.getControlState());
        assertNull(store.getTenantPolicy(9L));
    }

    /** F-R5-03: 合法 factor 规范化为大写后可用 */
    @Test
    void fr503_validFactorNormalized() {
        policy.confirmGlobalPolicy(MfaMode.REQUIRED, Set.of("totp"));
        assertEquals("TOTP", store.getControlState().getGlobalAllowedFactors());
        assertTrue(policy.readControlTuple().isUsable());
    }

    /** F-R5-02: SQL 仅允许 seed 或 OFF→非OFF 升级；禁止同 mode factor 覆写 */
    @Test
    void fr502_migrationSqlBlocksSameModeFactorRewrite() throws Exception {
        Path p = findMigrationSql();
        assertNotNull(p);
        String sql = Files.readString(p);
        assertTrue(sql.contains("legacy_global_merged"));
        // must require control mode OFF for non-seed upgrade path
        assertTrue(sql.contains("UPPER(IFNULL(c.global_mode, ''OFF'')) = ''OFF''")
                        || sql.contains("UPPER(IFNULL(c.global_mode, 'OFF')) = 'OFF'"),
                "OFF-only upgrade path");
        // must explicitly refuse OPTIONAL/REQUIRED control merge (same-mode factor drift)
        assertTrue(sql.contains("NOT IN (''OPTIONAL'', ''REQUIRED'')")
                        || sql.contains("NOT IN ('OPTIONAL', 'REQUIRED')"),
                "must block merge when control already non-OFF");
        assertTrue(sql.contains("GROUP_CONCAT") && sql.contains("ORDER BY"));
    }

    /** clean empty 路径保持 */
    @Test
    void fr5_cleanEmptyStillWorks() {
        long e = policy.confirmTenantPolicy(1L, MfaMode.REQUIRED, Set.of("SMS"));
        assertTrue(e >= 1);
        assertTrue(policy.readControlTuple().isUsable());
        MfaTenantPolicyDO t = store.getTenantPolicy(1L);
        assertNotNull(t);
        assertEquals("SMS", t.getAllowedFactors());
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
