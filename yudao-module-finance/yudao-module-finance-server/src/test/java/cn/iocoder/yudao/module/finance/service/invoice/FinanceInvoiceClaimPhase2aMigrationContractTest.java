package cn.iocoder.yudao.module.finance.service.invoice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * phase2a 开票/认领 DDL 迁移契约：表、列、存量回填、幂等模式。
 */
class FinanceInvoiceClaimPhase2aMigrationContractTest {

    private static final String MIGRATION = "sql/mysql/finance_invoice_claim_phase2a.sql";

    @Test
    void migrationShouldCreateInvoiceApplicationTables() throws IOException {
        String sql = readMigration();
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(lower.contains("create table if not exists `finance_invoice_application`"));
        assertTrue(lower.contains("create table if not exists `finance_invoice_application_line`"));
        assertTrue(sql.contains("`application_no`"));
        assertTrue(sql.contains("`process_instance_id`"));
        assertTrue(sql.contains("`approval_status`"));
        assertTrue(sql.contains("`issue_status`"));
        assertTrue(sql.contains("`total_amount`"));
        assertTrue(sql.contains("`confirmed_claimed_amount`"));
        assertTrue(sql.contains("`pending_claimed_amount`"));
        assertTrue(sql.contains("`applicant_user_id`"));
        assertTrue(sql.contains("`buyer_name`"));
        assertTrue(sql.contains("`buyer_tax_no`"));
        assertTrue(sql.contains("`tax_content`"));
        assertTrue(sql.contains("`voided`"));
        assertTrue(sql.contains("`tenant_id`"));
        assertTrue(sql.contains("`business_order_id`"));
        assertTrue(sql.contains("`invoice_no`"));
        assertTrue(sql.contains("`file_url`"));
        assertTrue(!lower.contains("drop table"));
        assertTrue(!lower.contains("truncate"));
    }

    @Test
    void migrationShouldAddOccupiedAndPendingColumnsIdempotently() throws IOException {
        String sql = readMigration();
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("`invoiced_occupied_amount`"));
        assertTrue(sql.contains("finance_business_order"));
        assertTrue(sql.contains("finance_bank_receipt"));
        assertTrue(sql.contains("`pending_claimed_amount`"));
        assertTrue(lower.contains("information_schema"));
        assertTrue(lower.contains("prepare phase2a_statement"));
        assertTrue(lower.contains("default 0.00") || lower.contains("default 0"));
    }

    @Test
    void migrationShouldExtendClaimItemWithXorAndLegacyBackfill() throws IOException {
        String sql = readMigration();
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("`invoice_application_id`"));
        assertTrue(sql.contains("`claim_source`"));
        assertTrue(sql.contains("LEGACY_BO"));
        assertTrue(sql.contains("INVOICE") || lower.contains("invoice"));
        assertTrue(lower.contains("update `finance_receipt_claim_item`"));
        assertTrue(sql.contains("SET `claim_source` = 'LEGACY_BO'")
                        || sql.contains("claim_source` = 'LEGACY_BO'"),
                "存量须回填 LEGACY_BO");
        // business_order_id 可空（XOR）
        assertTrue(lower.contains("modify column `business_order_id`")
                        || lower.contains("modify column `business_order_id` bigint default null"),
                "business_order_id 须改为可空以支持 XOR");
        assertFalse(lower.contains("check ("), "一期不强制 DB CHECK");
    }

    @Test
    void vbenMirrorShouldExistWithSameCoreMarkers() throws IOException {
        Path mirror = findRepositoryRoot().resolve("ruoyi-office-vben/sql/mysql/finance_invoice_claim_phase2a.sql");
        assertTrue(Files.exists(mirror), "vben 镜像迁移须存在");
        String sql = Files.readString(mirror);
        assertTrue(sql.contains("finance_invoice_application"));
        assertTrue(sql.contains("LEGACY_BO"));
        assertTrue(sql.contains("invoiced_occupied_amount"));
        assertTrue(sql.contains("pending_claimed_amount"));
    }

    private static String readMigration() throws IOException {
        Path migration = findRepositoryRoot().resolve(MIGRATION);
        assertTrue(Files.exists(migration), "phase2a 迁移文件必须存在: " + MIGRATION);
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
