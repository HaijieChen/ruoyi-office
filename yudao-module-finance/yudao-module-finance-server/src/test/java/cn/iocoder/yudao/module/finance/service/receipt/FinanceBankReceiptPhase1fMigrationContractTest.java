package cn.iocoder.yudao.module.finance.service.receipt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceBankReceiptPhase1fMigrationContractTest {

    @Test
    void migrationShouldCreateImmutableLifecycleAuditTableIdempotently() throws IOException {
        String sql = readMigration();
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        assertTrue(lowerSql.contains("create table if not exists `finance_receipt_lifecycle_audit`"));
        assertTrue(sql.contains("`receipt_id`"));
        assertTrue(sql.contains("`action`"));
        assertTrue(sql.contains("`operator_id`"));
        assertTrue(sql.contains("`action_time`"));
        assertTrue(sql.contains("`reason`"));
        assertTrue(sql.contains("`tenant_id`"));
        assertTrue(sql.contains("`deleted`"));
        assertTrue(sql.contains("idx_receipt_id_action_time"));
        assertTrue(!lowerSql.contains("drop table"));
        assertTrue(!lowerSql.contains("truncate"));
    }

    @Test
    void migrationShouldRegisterAllLifecyclePermissionsIdempotently() throws IOException {
        String sql = readMigration();
        String lowerSql = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("finance:receipt:close"));
        assertTrue(sql.contains("finance:receipt:reopen"));
        assertTrue(sql.contains("finance:receipt:audit-query"));
        assertTrue(sql.contains("'银行到款关闭'"));
        assertTrue(sql.contains("'银行到款重开'"));
        assertTrue(sql.contains("'银行到款操作审计查询'"));
        assertTrue(lowerSql.contains("where not exists"));
        assertTrue(lowerSql.contains("update `system_menu`"));
        assertTrue(sql.contains("component` = 'finance/receipt/index'"));
    }

    private static String readMigration() throws IOException {
        Path migration = findRepositoryRoot().resolve("sql/mysql/finance_bank_receipt_phase1f.sql");
        assertTrue(Files.exists(migration), "phase1f 迁移文件必须存在");
        return Files.readString(migration);
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("无法定位仓库根目录");
        }
        return current;
    }
}
