package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaUserAssuranceDO;
import cn.iocoder.yudao.module.system.service.mfa.contract.MfaAuthLoginResult;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLifecycleState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaLoginStatus;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaControlTuple;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.support.MfaChecksumUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.MFA_POLICY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 1 最终审查 FAIL 反例回归（F-S1-01～05）。
 * 旧 SHA 上这些断言失败；本修复后必须通过。
 */
public class MfaSlice1FinalFailRegressionTest extends BaseMockitoUnitTest {

    private InMemoryMfaAuthorityStore store;
    private MfaPolicyAuthorityImpl policy;
    private MfaAssuranceAuthorityImpl assurance;

    @BeforeEach
    void setUp() {
        store = new InMemoryMfaAuthorityStore();
        policy = new MfaPolicyAuthorityImpl(store);
        assurance = new MfaAssuranceAuthorityImpl(store);
    }

    /** F-S1-01: 缺 control + confirmed tenant REQUIRED → closed，不得 usable OFF */
    @Test
    void fS101_missingControlWithRequiredTenant_closed() {
        store.saveTenantPolicy(MfaTenantPolicyDO.builder()
                .tenantId(9L)
                .mode(MfaMode.REQUIRED.name())
                .allowedFactors("TOTP")
                .policyEpoch(1L)
                .minAcceptedEpoch(1L)
                .checksum(MfaChecksumUtil.compute("REQUIRED", Set.of("TOTP"), 1L))
                .confirmed(true)
                .build());
        MfaControlTuple t = policy.readControlTuple();
        assertFalse(t.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, t.getLifecycleState());
        assertNotEquals(MfaMode.OFF, t.getGlobalMode());
    }

    /** F-S1-01: UNINITIALIZED + 非法 global_mode → closed */
    @Test
    void fS101_uninitializedIllegalMode_closed() {
        store.saveControlState(MfaControlStateDO.builder()
                .id(1L)
                .lifecycleState(MfaLifecycleState.UNINITIALIZED.name())
                .globalMode("NOT_A_MODE")
                .globalPolicyEpoch(0L)
                .globalMinAcceptedEpoch(0L)
                .checksum("x")
                .build());
        MfaControlTuple t = policy.readControlTuple();
        assertFalse(t.isUsable());
        assertEquals(MfaLifecycleState.DEGRADED_CLOSED, t.getLifecycleState());
    }

    /** F-S1-03: tenant-first REQUIRED 同事务 ARMED 且可用 */
    @Test
    void fS103_tenantFirstRequired_armsAndUsable() {
        long epoch = policy.confirmTenantPolicy(101L, MfaMode.REQUIRED, Set.of("TOTP"));
        assertTrue(epoch >= 1);
        MfaControlTuple t = policy.readControlTuple();
        assertTrue(t.isUsable());
        assertEquals(MfaLifecycleState.ARMED, t.getLifecycleState());
        // global 仍可为 OFF（紧急总开关），但已 ARMED
        assertEquals(MfaMode.OFF, t.getGlobalMode());
        MfaPolicySnapshot tenant = policy.resolveEffectivePolicy(101L);
        // global OFF 覆盖 tenant REQUIRED
        assertTrue(tenant.isUsable());
        assertEquals(MfaMode.OFF, tenant.getMode());
    }

    /** F-S1-03: tenant-first OPTIONAL 后租户有效模式 OPTIONAL */
    @Test
    void fS103_tenantFirstOptional_effectiveOptional() {
        // 先 global OPTIONAL 再 tenant 才有下放；tenant-first OPTIONAL 会 ARMED + global OFF → 有效 OFF
        // 正确路径：global OPTIONAL 后 tenant REQUIRED
        policy.confirmGlobalPolicy(MfaMode.OPTIONAL, Set.of("TOTP"));
        policy.confirmTenantPolicy(7L, MfaMode.REQUIRED, Set.of("TOTP"));
        MfaPolicySnapshot s = policy.resolveEffectivePolicy(7L);
        assertTrue(s.isUsable());
        assertEquals(MfaMode.REQUIRED, s.getMode());
        assertEquals(MfaLifecycleState.ARMED, s.getLifecycleState());
    }

    /** F-S1-04: 损坏 enrollment_state → fail-closed */
    @Test
    void fS104_corruptEnrollment_closed() {
        store.saveUserAssurance(MfaUserAssuranceDO.builder()
                .tenantId(1L).userId(2L)
                .enabled(false)
                .enrollmentState("CORRUPT")
                .assuranceEpoch(3L)
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> assurance.requireAssuranceForAdmin(1L, 2L));
        assertEquals(MFA_POLICY_UNAVAILABLE.getCode(), ex.getCode());
    }

    /** F-S1-04: null assurance_epoch → fail-closed，不得猜 0 */
    @Test
    void fS104_nullEpoch_closed() {
        store.saveUserAssurance(MfaUserAssuranceDO.builder()
                .tenantId(1L).userId(3L)
                .enabled(false)
                .enrollmentState("NONE")
                .assuranceEpoch(null)
                .build());
        assertThrows(ServiceException.class, () -> assurance.getAssurance(1L, 3L));
    }

    /** F-S1-02: 非法 AUTHENTICATED 无 token 拒绝 */
    @Test
    void fS102_authenticatedWithoutTokens_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MfaAuthLoginResult.authenticated(null, "r", 1));
        assertThrows(IllegalArgumentException.class,
                () -> MfaAuthLoginResult.authenticated("a", null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MfaAuthLoginResult.authenticated("a", "r", 0));
    }

    /** F-S1-02: MFA_REQUIRED 必须配 PRE_AUTH */
    @Test
    void fS102_statusClassMismatch_rejected() {
        assertThrows(IllegalArgumentException.class, () ->
                MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_REQUIRED,
                        MfaAuthLoginResult.FlowPayload.of("tok", MfaFlowTokenClass.RECOVERY, 300)));
        assertThrows(IllegalArgumentException.class, () ->
                MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_POLICY_UNAVAILABLE,
                        MfaAuthLoginResult.FlowPayload.of("tok", MfaFlowTokenClass.PRE_AUTH, 300)));
        assertThrows(IllegalArgumentException.class, () ->
                MfaAuthLoginResult.FlowPayload.of("tok", MfaFlowTokenClass.PRE_AUTH, -1));
    }

    /** F-S1-02: 合法映射通过 */
    @Test
    void fS102_legalMappings_ok() {
        var pre = MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of("f1", MfaFlowTokenClass.PRE_AUTH, 300));
        assertFalse(pre.hasAccessOrRefreshToken());
        var enr = MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_ENROLLMENT_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of("f2", MfaFlowTokenClass.ENROLLMENT, 600));
        assertEquals(MfaFlowTokenClass.ENROLLMENT, enr.getFlow().getTokenClass());
        var rec = MfaAuthLoginResult.mfaFlow(MfaLoginStatus.MFA_RECOVERY_REQUIRED,
                MfaAuthLoginResult.FlowPayload.of("f3", MfaFlowTokenClass.RECOVERY, 600));
        assertEquals(MfaFlowTokenClass.RECOVERY, rec.getFlow().getTokenClass());
    }

    /** F-S1-05: 迁移脚本包含升级路径（ADD COLUMN / policy_version 回填） */
    @Test
    void fS105_migrationSqlContainsUpgradePath() throws Exception {
        Path p = findMigrationSql();
        assertNotNull(p, "system_mfa_v3_authority.sql must exist");
        String sql = Files.readString(p);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `system_mfa_control_state`"));
        assertTrue(sql.contains("global_policy_epoch"));
        assertTrue(sql.contains("information_schema.COLUMNS"), "must probe existing columns");
        assertTrue(sql.contains("ALTER TABLE `system_mfa_control_state` ADD COLUMN"),
                "must ALTER-add missing v3 columns");
        assertTrue(sql.contains("policy_version"), "must backfill from v2 policy_version");
        assertTrue(sql.contains("system_mfa_user_assurance"));
    }

    private static Path findMigrationSql() {
        Path[] candidates = {
                Path.of("sql/mysql/system_mfa_v3_authority.sql"),
                Path.of("oa/sql/mysql/system_mfa_v3_authority.sql"),
                Path.of("../sql/mysql/system_mfa_v3_authority.sql"),
                Path.of("../../sql/mysql/system_mfa_v3_authority.sql"),
                Path.of("../../../sql/mysql/system_mfa_v3_authority.sql"),
                Path.of("../../../../sql/mysql/system_mfa_v3_authority.sql"),
                Path.of("../../../../../sql/mysql/system_mfa_v3_authority.sql"),
        };
        // also walk up from user.dir
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
        for (Path c : candidates) {
            if (Files.isRegularFile(c)) {
                return c;
            }
        }
        return null;
    }
}
