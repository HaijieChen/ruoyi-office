package cn.iocoder.yudao.module.finance.service.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CS-T1：合同签约 DDL 迁移契约（幂等、列集、BO 正式关联、不做「审批完成证明」列）。
 */
class FinanceContractApplicationMigrationContractTest {

    @Test
    void phase1MigrationShouldCreateContractApplicationAndBoFkIdempotently() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Path migration = repositoryRoot.resolve("sql/mysql/finance_contract_application_phase1.sql");
        assertTrue(Files.exists(migration), "CS-T1 migration must exist");

        String sql = Files.readString(migration);
        String lower = sql.toLowerCase(Locale.ROOT);

        assertTrue(lower.contains("create table if not exists `finance_contract_application`"));
        assertTrue(lower.contains("information_schema"));
        assertTrue(lower.contains("prepare"));
        assertTrue(lower.contains("`contract_application_id`"));
        assertTrue(lower.contains("idx_contract_application_id"));
        assertTrue(sql.contains("contract_process_id"), "legacy column retained after formal FK");
        // C22: no physical column for 审批流程已完成证明 (comment-only mentions allowed)
        assertFalse(lower.matches("(?s).*`[^`]*proof[^`]*`.*")
                        || lower.contains("`approval_complete")
                        || lower.contains("`complete_proof"),
                "C22: must not create proof/完成证明 column");
        int tableStart = lower.indexOf("create table if not exists `finance_contract_application`");
        int tableEnd = lower.indexOf(") engine=innodb", tableStart);
        String tableDef = lower.substring(tableStart, tableEnd);
        assertFalse(tableDef.contains("证明"), "C22: table body must not define 证明 column");
    }

    @Test
    void contractApplicationTableShouldContainFbAnd722ColumnGroups() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        String sql = Files.readString(repositoryRoot.resolve("sql/mysql/finance_contract_application_phase1.sql"));
        String lower = sql.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("create table if not exists `finance_contract_application`");
        int end = lower.indexOf(") engine=innodb", start);
        assertTrue(start >= 0 && end > start);
        String tableDef = lower.substring(start, end);

        for (String column : List.of(
                "application_no", "process_instance_id", "approval_status",
                "current_node_key", "current_node_name", "applicant_user_id",
                "counterparty_company_id", "counterparty_name", "amount_na", "contract_amount",
                "sign_company", "file_name", "file_type", "product_type", "rebate_ratio",
                "settlement_method", "copy_count", "seal_types", "need_mail", "mail_address",
                "pre_process_ref", "start_date", "end_date",
                "draft_file_url", "seal_file_url", "actual_sealer_user_id", "archived_at",
                "mail_tracking_no", "voided", "tenant_id", "deleted")) {
            assertTrue(tableDef.contains("`" + column + "`"), "missing column: " + column);
        }
        assertTrue(tableDef.contains("uk_contract_app_no_tenant_deleted"));
    }

    @Test
    void vbenSqlMirrorShouldExist() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Path mirror = repositoryRoot.resolve("ruoyi-office-vben/sql/mysql/finance_contract_application_phase1.sql");
        assertTrue(Files.exists(mirror), "vben SQL mirror required");
        assertTrue(Files.readString(mirror).toLowerCase(Locale.ROOT)
                .contains("finance_contract_application"));
    }

    @Test
    void fieldContractDocShouldExistAndReferenceDdl() throws IOException {
        Path repositoryRoot = findRepositoryRoot();
        Path doc = repositoryRoot.resolve(
                "docs/superpowers/specs/2026-07-31-finance-contract-application-field-contract.md");
        assertTrue(Files.exists(doc));
        String text = Files.readString(doc);
        assertTrue(text.contains("finance_contract_application_phase1.sql"));
        assertTrue(text.contains("needMail") || text.contains("need_mail"));
        assertTrue(text.contains("contract_application_id"));
        assertTrue(text.contains("amount_na") || text.contains("amountNa"));
        assertTrue(text.contains("审批流程已完成证明") || text.contains("C22"));
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
