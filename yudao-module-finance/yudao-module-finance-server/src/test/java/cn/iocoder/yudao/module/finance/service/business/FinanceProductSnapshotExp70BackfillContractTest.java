package cn.iocoder.yudao.module.finance.service.business;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EXP-70 包③：无歧义回填 SQL 幂等、镜像与「不静默改写冲突」契约。
 */
class FinanceProductSnapshotExp70BackfillContractTest {

    @Test
    void backfillMigrationShouldBeIdempotentSafeAndMirrored() throws IOException {
        Path root = findRepositoryRoot();
        Path primary = root.resolve("sql/mysql/finance_product_snapshot_backfill_exp70.sql");
        Path mirrored = root.resolve("ruoyi-office-vben/sql/mysql/finance_product_snapshot_backfill_exp70.sql");

        assertTrue(Files.exists(primary), "backfill script must exist");
        assertTrue(Files.exists(mirrored), "backfill script must be mirrored");
        assertEquals(Files.readString(primary), Files.readString(mirrored));

        String sql = Files.readString(primary);
        String lower = sql.toLowerCase(Locale.ROOT);

        // 回填对象
        assertTrue(lower.contains("product_type_snapshot"));
        assertTrue(lower.contains("tax_content"));
        assertTrue(lower.contains("finance_business_order"));
        assertTrue(lower.contains("finance_invoice_application_line"));
        assertTrue(lower.contains("finance_contract_application"));

        // 复审 #5：权威条件与正常写路径对齐
        assertTrue(lower.contains("approval_status") && lower.contains("approved"),
                "backfill must require APPROVED");
        assertTrue(lower.contains("voided"), "backfill must check voided");
        assertTrue(lower.contains("importer_id") && lower.contains("applicant_user_id"),
                "backfill must match importer to applicant");
        assertTrue(sql.contains("I_bo_contract_authority_invalid"));
        // 复审 #3：I 用 LEFT JOIN + NULL-safe，覆盖缺失合同
        assertTrue(lower.contains("left join finance_contract_application"),
                "audit I must LEFT JOIN contracts to catch missing/deleted");
        assertTrue(lower.contains("ca.id is null"),
                "audit I must count missing contracts");
        // 复审 #1：E 计入空/NULL 表头
        assertTrue(sql.contains("E_tax_content_mismatch"));
        assertTrue(lower.contains("a.tax_content is null or trim(a.tax_content) = ''"),
                "audit E must treat blank tax_content as mismatch when lines complete");
        // 复审 #2：F_bo 可开票余额谓词
        assertTrue(sql.contains("F_bo_snapshot_still_empty_with_contract"));
        assertTrue(lower.contains("settlement_amount > ifnull"),
                "F_bo must use openable balance predicate");

        // 仅空快照写入（幂等保护）
        assertTrue(lower.contains("product_type_snapshot is null")
                || lower.contains("trim(bo.product_type_snapshot) = ''"));
        assertTrue(lower.contains("tax_content is null")
                || lower.contains("trim(a.tax_content) = ''"));

        assertTrue(sql.contains("A_contract_product_empty") || sql.contains("audit_key"));
        assertTrue(sql.contains("C_bo_snapshot_conflict") || lower.contains("conflict"));
        assertTrue(sql.contains("D_invoice_mixed_products") || lower.contains("mixed"));
        assertTrue(sql.contains("G_line_source_contract_unproven"));
        assertTrue(sql.contains("H_line_product_unproven"));

        assertFalse(lower.contains("set l.source_contract_application_id = bo.contract_application_id")
                || lower.contains("set l.source_contract_application_id=bo.contract_application_id"));
        assertFalse(lower.contains("set l.product_type_snapshot"),
                "historical invoice line product must not be auto-backfilled from current BO");

        assertTrue(lower.contains("l.product_type_snapshot") || lower.contains("line_snapshot"));
        assertFalse(lower.contains("set a.tax_content = '软件'"));
        assertTrue(lower.contains("deleted = b'0'") || lower.contains("deleted=b'0'"));
    }

    @Test
    void readonlyAuditScriptMustContainNoUpdatesAndMirror() throws IOException {
        Path root = findRepositoryRoot();
        Path primary = root.resolve("sql/mysql/finance_product_snapshot_audit_readonly_exp70.sql");
        Path mirrored = root.resolve("ruoyi-office-vben/sql/mysql/finance_product_snapshot_audit_readonly_exp70.sql");
        assertTrue(Files.exists(primary));
        assertTrue(Files.exists(mirrored));
        assertEquals(Files.readString(primary), Files.readString(mirrored));
        String sql = Files.readString(primary);
        String lower = sql.toLowerCase(Locale.ROOT);
        assertFalse(lower.matches("(?s).*\\bupdate\\s+finance_.*"),
                "readonly audit must not contain UPDATE");
        assertTrue(lower.contains("a_contract_product_empty"));
        assertTrue(lower.contains("h_line_product_unproven"));
        assertTrue(sql.contains("I_bo_contract_authority_invalid"));
        // 复审 #1/#2/#3 与 Backfill 内嵌审计同步
        assertTrue(lower.contains("left join finance_contract_application"));
        assertTrue(lower.contains("ca.id is null"));
        assertTrue(lower.contains("a.tax_content is null or trim(a.tax_content) = ''"));
        assertTrue(lower.contains("settlement_amount > ifnull"));
        assertTrue(sql.contains("E_tax_content_mismatch"));
        assertTrue(sql.contains("F_bo_snapshot_still_empty_with_contract"));
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (Path p = current; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("sql/mysql"))
                    && Files.exists(p.resolve("yudao-module-finance"))) {
                return p;
            }
        }
        throw new IllegalStateException("cannot locate repository root from " + current);
    }
}
