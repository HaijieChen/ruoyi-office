package cn.iocoder.yudao.module.finance.service.business;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceBusinessOrderMigrationContractTest {

    @Test
    void importMigrationShouldBeIdempotentAndInstallColumnsUniqueHashAndPermission() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Path migration = repositoryRoot.resolve("sql/mysql/finance_business_order_import_phase1d.sql");

        assertTrue(Files.exists(migration), "OA-16 migration must exist");
        String sql = Files.readString(migration);
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        assertTrue(lowerSql.contains("information_schema"));
        assertTrue(sql.contains("`order_date`"));
        assertTrue(sql.contains("`product_name`"));
        assertTrue(sql.contains("`contact_person`"));
        assertTrue(sql.contains("`execution_start_date`"));
        assertTrue(sql.contains("`execution_end_date`"));
        assertTrue(sql.contains("`payer_name`"));
        assertTrue(sql.contains("`signed_execution_amount`"));
        assertTrue(sql.contains("`discount_rate`"));
        assertTrue(sql.contains("`settlement_amount`"));
        assertTrue(sql.contains("`bank_account`"));
        assertTrue(sql.contains("`source_row_hash`"));
        assertTrue(sql.contains("`uk_source_row_hash_tenant_deleted`"));
        assertTrue(sql.contains("finance:business-order:import"));
        assertTrue(lowerSql.contains("where not exists"));
        assertTrue(lowerSql.contains("modify column `business_subject`")
                && lowerSql.contains("business_subject` varchar(255) default null"));
        assertTrue(lowerSql.contains("column_name` = 'business_subject'"),
                "MODIFY COLUMN business_subject must be guarded by information_schema for repeatable execution after phase1e drops it");
    }

    @Test
    void sheetReplacementMigrationShouldGuardDataBeforeDroppingGenericColumns() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Path migration = repositoryRoot.resolve("sql/mysql/finance_business_order_sheet_phase1e.sql");

        assertTrue(Files.exists(migration), "phase 1E migration must exist");
        String sql = Files.readString(migration);
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        String normalizedSql = lowerSql.replaceAll("\\s+", " ");

        assertTrue(lowerSql.contains("mysqldump")
                && lowerSql.contains("finance_receipt_claim_item")
                && lowerSql.contains("finance_bank_receipt"));
        assertTrue(lowerSql.contains("information_schema"));
        for (String column : List.of("import_date", "importer_id", "contract_process_id")) {
            assertTrue(lowerSql.contains("`column_name` = '" + column + "'"));
            assertTrue(lowerSql.contains("add column `" + column + "`"));
        }
        assertTrue(normalizedSql.contains("update `finance_business_order` set `settlement_amount` = `receivable_amount` where `settlement_amount` is null"));
        assertTrue(normalizedSql.contains("`confirmed_claimed_amount` > `settlement_amount`"));
        assertTrue(lowerSql.contains("phase_1e_refused_confirmed_claims_exceed_settlement"));

        int unsafeBalanceGuard = lowerSql.indexOf("phase_1e_refused_confirmed_claims_exceed_settlement");
        int firstDrop = lowerSql.indexOf("drop column");
        assertTrue(unsafeBalanceGuard >= 0 && firstDrop > unsafeBalanceGuard);
        for (String column : List.of("business_subject", "business_type", "contract_ref", "project_ref",
                "receivable_amount", "payable_amount", "owner_id", "status")) {
            assertTrue(lowerSql.contains("drop column `" + column + "`"));
        }
    }

    @Test
    void sheetReplacementMigrationShouldNotRequireStoredRoutinePrivileges() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        String lowerSql = Files.readString(repositoryRoot.resolve(
                "sql/mysql/finance_business_order_sheet_phase1e.sql")).toLowerCase(Locale.ROOT);

        assertFalse(lowerSql.contains("create procedure") || lowerSql.contains("drop procedure"),
                "phase 1E 必须兼容无 CREATE/ALTER ROUTINE 权限的应用迁移账号");
        assertTrue(lowerSql.contains("phase_1e_refused_confirmed_claims_exceed_settlement"),
                "无存储过程的保护分支必须保留可识别的拒绝原因");
    }

    @Test
    void baseSchemaShouldMatchTheFinalSheetAndClaimBalanceContract() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        String sql = Files.readString(repositoryRoot.resolve("sql/mysql/finance_business_order_phase1b.sql"));
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        String tableDefinition = lowerSql.substring(lowerSql.indexOf("create table if not exists `finance_business_order`"),
                lowerSql.indexOf(") engine=innodb"));

        for (String column : List.of("order_no", "import_date", "importer_id", "contract_process_id", "order_date",
                "product_name", "contact_person", "execution_start_date", "execution_end_date", "payer_name",
                "signed_execution_amount", "discount_rate", "settlement_amount", "bank_account", "remark",
                "confirmed_claimed_amount", "source_row_hash", "tenant_id", "creator", "create_time", "updater",
                "update_time", "deleted")) {
            assertTrue(tableDefinition.contains("`" + column + "`"));
        }
        for (String column : List.of("business_subject", "business_type", "contract_ref", "project_ref",
                "receivable_amount", "payable_amount", "owner_id", "status")) {
            assertTrue(!tableDefinition.contains("`" + column + "`"));
        }
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("Unable to locate repository root from " + Path.of("").toAbsolutePath());
        }
        return current;
    }

}
