package cn.iocoder.yudao.module.finance.service.claim;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceReceiptClaimPhase1eMigrationContractTest {

    @Test
    void migrationShouldAddRevokeReasonColumnIdempotently() throws IOException {
        String sql = readPhase1e();
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        assertTrue(lowerSql.contains("revoke_reason"),
                "必须包含 revoke_reason 列的幂等添加");
        assertTrue(lowerSql.contains("information_schema"),
                "必须使用 information_schema 检查保证幂等");
        assertTrue(lowerSql.contains("prepare phase1e_statement"),
                "必须使用 PREPARE/EXECUTE 幂等模式");
        assertTrue(sql.contains("'撤销原因'"),
                "列注释必须为 '撤销原因'");
    }

    @Test
    void migrationShouldCreateRevokeAuditTable() throws IOException {
        String sql = readPhase1e();
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        assertTrue(lowerSql.contains("create table if not exists"),
                "必须使用 CREATE TABLE IF NOT EXISTS 保证幂等");
        assertTrue(sql.contains("finance_receipt_claim_revoke_audit"),
                "必须创建 finance_receipt_claim_revoke_audit 表");
        assertTrue(sql.contains("`claim_id`"),
                "审计表必须包含 claim_id 列");
        assertTrue(sql.contains("`reviewer_id`"),
                "审计表必须包含 reviewer_id 列");
        assertTrue(sql.contains("`revoke_time`"),
                "审计表必须包含 revoke_time 列");
        assertTrue(sql.contains("`revoke_reason`"),
                "审计表必须包含 revoke_reason 列");
        assertTrue(sql.contains("`tenant_id`"),
                "审计表必须包含 tenant_id 列（多租户）");
        assertTrue(sql.contains("`deleted`"),
                "审计表必须包含 deleted 列（软删除）");
        assertTrue(sql.contains("idx_claim_id"),
                "审计表必须在 claim_id 上建索引");
        assertTrue(sql.contains("'财务到款认领撤销审计'"),
                "表注释必须为 '财务到款认领撤销审计'");
    }

    @Test
    void migrationShouldRegisterRevokePermissionIdempotently() throws IOException {
        String sql = readPhase1e();
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("finance:receipt-claim:revoke"),
                "必须注册 finance:receipt-claim:revoke 权限");
        assertTrue(sql.contains("'到款认领撤销'"),
                "权限名称必须为 '到款认领撤销'");
        assertTrue(lowerSql.contains("where not exists"),
                "INSERT 必须使用 WHERE NOT EXISTS 保证幂等");
        assertTrue(lowerSql.contains("update `system_menu`"),
                "必须包含 UPDATE 收敛语句");
        assertTrue(sql.contains("component` = 'finance/receipt-claim/index'"),
                "权限菜单必须挂载到 receipt-claim 页面菜单下");
        assertTrue(sql.contains(", 3, 17,"),
                "权限菜单 type=3, sort=17");
    }

    @Test
    void migrationShouldNotAlterPhase1cSemantics() throws IOException {
        String sql = readPhase1e();
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        assertTrue(lowerSql.contains("after `reject_reason`"),
                "revoke_reason 必须添加在 reject_reason 之后");
        assertTrue(!lowerSql.contains("drop table"),
                "不得包含 DROP TABLE 语句");
        assertTrue(!lowerSql.contains("drop column"),
                "不得包含 DROP COLUMN 语句");
        assertTrue(!lowerSql.contains("truncate"),
                "不得包含 TRUNCATE 语句");
    }

    private static String readPhase1e() throws IOException {
        Path migration = findRepositoryRoot().resolve("sql/mysql/finance_receipt_claim_phase1e.sql");
        assertTrue(Files.exists(migration), "phase1e 迁移文件必须存在");
        return Files.readString(migration);
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("无法从 " + Path.of("").toAbsolutePath() + " 定位仓库根目录");
        }
        return current;
    }

}
