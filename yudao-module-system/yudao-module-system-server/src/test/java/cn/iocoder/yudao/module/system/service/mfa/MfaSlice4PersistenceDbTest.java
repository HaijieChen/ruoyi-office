package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaFactorDO;
import cn.iocoder.yudao.module.system.dal.mysql.mfa.MfaFactorMapper;
import cn.iocoder.yudao.module.system.service.mfa.crypto.MfaSecretCipher;
import cn.iocoder.yudao.module.system.service.mfa.delivery.StubMfaChallengeDelivery;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaAuthFlowState;
import cn.iocoder.yudao.module.system.service.mfa.enums.MfaFlowTokenClass;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaAuthFlowRecord;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaIssuedFlow;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingTotp;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;
import cn.iocoder.yudao.module.system.service.mfa.store.MyBatisMfaAuthFlowStore;
import cn.iocoder.yudao.module.system.service.mfa.store.MyBatisMfaFactorStore;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 切片 4：MyBatis flow/factor CAS（H2）。
 */
@Import({MyBatisMfaAuthFlowStore.class, MyBatisMfaFactorStore.class,
        MfaAuthFlowServiceImpl.class, MfaFactorServiceImpl.class, StubMfaChallengeDelivery.class,
        MfaConfiguration.class})
public class MfaSlice4PersistenceDbTest extends BaseDbUnitTest {

    @Resource
    private MfaAuthFlowServiceImpl flowService;
    @Resource
    private MfaFactorServiceImpl factorService;
    @Resource
    private MfaFactorMapper factorMapper;
    @Resource
    private MfaSecretCipher secretCipher;
    @Resource
    private DataSource dataSource;

    @Test
    void flow_casComplete_persisted() {
        MfaPolicySnapshot snap = MfaPolicySnapshot.uninitializedOff();
        MfaIssuedFlow issued = flowService.issue(MfaFlowTokenClass.PRE_AUTH, 9L, 1L, "c",
                snap, 0L, List.of("verify"), List.of(), 300);
        MfaAuthFlowRecord active = flowService.resolveActive(issued.getFlowToken());
        assertNotNull(active);
        assertEquals(MfaAuthFlowState.ACTIVE, active.getState());
        assertTrue(flowService.tryComplete(issued.getFlowToken()));
        assertFalse(flowService.tryComplete(issued.getFlowToken()));
        assertNull(flowService.resolveActive(issued.getFlowToken()));
    }

    @Test
    void factor_casActivate_persisted() {
        MfaPendingTotp pending = factorService.startPendingTotp(1L, 20L, "carol");
        assertEquals("PENDING", factorService.peekFactorStatus(1L, 20L, pending.getFactorId()));
        assertTrue(factorService.tryActivatePendingFactor(1L, 20L, pending.getFactorId(), 99L));
        assertFalse(factorService.tryActivatePendingFactor(1L, 20L, pending.getFactorId(), 99L));
        assertEquals("ACTIVE", factorService.peekFactorStatus(1L, 20L, pending.getFactorId()));
        assertTrue(factorService.revertFactorToPending(1L, 20L, pending.getFactorId()));
        assertEquals("PENDING", factorService.peekFactorStatus(1L, 20L, pending.getFactorId()));
    }

    @Test
    void factor_uniqueKey_andCipherNotPlaintext() {
        MfaPendingTotp pending = factorService.startPendingTotp(2L, 21L, "dave");
        MfaFactorDO row = factorMapper.selectByUserAndKey(2L, 21L, pending.getFactorId());
        assertNotNull(row);
        assertNotEquals("plain-dev", row.getKeyId());
        assertNotEquals(pending.getSecretManual(), row.getSecretCiphertext());
        assertEquals(pending.getSecretManual(), secretCipher.decrypt(row.getKeyId(), row.getSecretCiphertext()));

        MfaFactorDO dup = MfaFactorDO.builder()
                .tenantId(2L).userId(21L).factorKey(pending.getFactorId())
                .type("TOTP").status("PENDING").secretCiphertext("x").keyId("test-k1")
                .build();
        assertThrows(Exception.class, () -> factorMapper.insert(dup));
    }

    @Test
    void factorKey_legacyCollision_backfillThenUnique() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS mfa_fk_probe");
        jdbcTemplate.execute("""
                CREATE TABLE mfa_fk_probe (
                  id bigint NOT NULL,
                  tenant_id bigint NOT NULL,
                  user_id bigint NOT NULL,
                  factor_key varchar(64),
                  deleted bit NOT NULL DEFAULT FALSE,
                  PRIMARY KEY (id)
                )
                """);
        jdbcTemplate.update("INSERT INTO mfa_fk_probe(id,tenant_id,user_id,factor_key) VALUES (1,1,1,'')");
        jdbcTemplate.update("INSERT INTO mfa_fk_probe(id,tenant_id,user_id,factor_key) VALUES (2,1,1,'legacy-1')");
        jdbcTemplate.update("""
                UPDATE mfa_fk_probe
                SET factor_key = CONCAT('legacy-', id, '-', REPLACE(CAST(RANDOM_UUID() AS VARCHAR), '-', ''))
                WHERE factor_key IS NULL OR TRIM(factor_key) = ''
                """);
        jdbcTemplate.update("""
                UPDATE mfa_fk_probe t
                SET factor_key = CONCAT(factor_key, '-d', id)
                WHERE id IN (
                  SELECT id FROM (
                    SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id, user_id, factor_key ORDER BY id) rn
                    FROM mfa_fk_probe
                  ) x WHERE rn > 1
                )
                """);
        jdbcTemplate.execute(
                "ALTER TABLE mfa_fk_probe ADD CONSTRAINT uk_mfa_fk_probe UNIQUE (tenant_id, user_id, factor_key)");
        var keys = jdbcTemplate.queryForList("SELECT factor_key FROM mfa_fk_probe ORDER BY id", String.class);
        assertEquals(2, keys.size());
        assertNotEquals(keys.get(0), keys.get(1));
        assertNotEquals("legacy-1", keys.get(0));
        assertEquals("legacy-1", keys.get(1));
        jdbcTemplate.execute("DROP TABLE IF EXISTS mfa_fk_probe");
    }

    @Test
    void slice4Sql_existsAndAddsFactorKey() throws Exception {
        Path p = findSlice4Sql();
        assertNotNull(p, "system_mfa_v3_slice4_cas.sql must exist");
        String sql = Files.readString(p);
        assertTrue(sql.contains("factor_key"));
        assertTrue(sql.contains("system_mfa_auth_flow"));
        assertTrue(sql.contains("system_mfa_factor"));
    }

    private static Path findSlice4Sql() {
        Path cwd = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            Path hit = cwd.resolve("sql/mysql/system_mfa_v3_slice4_cas.sql");
            if (Files.isRegularFile(hit)) {
                return hit;
            }
            hit = cwd.resolve("oa/sql/mysql/system_mfa_v3_slice4_cas.sql");
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
