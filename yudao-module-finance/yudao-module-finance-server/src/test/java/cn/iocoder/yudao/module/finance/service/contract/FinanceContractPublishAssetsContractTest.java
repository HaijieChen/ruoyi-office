package cn.iocoder.yudao.module.finance.service.contract;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CS-T7：发布清单与关键资产存在性（DDL/菜单/BPMN/文档/前端入口）。
 */
class FinanceContractPublishAssetsContractTest {

    @Test
    void publishAssetsShouldExist() throws Exception {
        Path root = findRepositoryRoot();
        String[] paths = {
                "sql/mysql/finance_contract_application_phase1.sql",
                "sql/mysql/finance_contract_application_menu_phase1.sql",
                "sql/mysql/finance_contract_bpmn_diagram_di.sql",
                "sql/mysql/bpmn/finance_contract_sign.bpmn20.xml",
                "docs/superpowers/specs/2026-07-31-finance-contract-application-field-contract.md",
                "docs/superpowers/specs/2026-07-31-finance-contract-bpm-delegate.md",
                "docs/superpowers/specs/2026-07-31-finance-contract-publish-checklist.md",
                "docs/superpowers/specs/2026-07-31-finance-contract-regression.md",
                "ruoyi-office-vben/apps/web-antd/src/api/finance/contract-application/index.ts",
                "ruoyi-office-vben/apps/web-antd/src/views/finance/contract-application/index.vue",
                "ruoyi-office-vben/apps/web-antd/src/views/finance/contract-application/modules/form.vue",
                "ruoyi-office-vben/apps/web-antd/src/views/finance/contract-application/modules/info.vue",
        };
        for (String p : paths) {
            assertTrue(Files.exists(root.resolve(p)), "missing: " + p);
        }
    }

    @Test
    void publishChecklistShouldCoverDiMenuAndSmoke() throws Exception {
        Path root = findRepositoryRoot();
        String text = Files.readString(root.resolve(
                "docs/superpowers/specs/2026-07-31-finance-contract-publish-checklist.md"));
        assertTrue(text.contains("finance_contract_sign"));
        assertTrue(text.contains("BPMNDiagram") || text.contains("DI"));
        assertTrue(text.contains("finance_contract_application_menu_phase1.sql"));
        assertTrue(text.contains("重新发布"));
        assertTrue(text.contains("createAndStart") || text.contains("S1"));
        assertTrue(text.contains("has_di"));
    }

    @Test
    void controllerShouldNotExposeUserCallableOutcomeBypass() throws Exception {
        String controller = Files.readString(findRepositoryRoot().resolve(
                "yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/controller/admin/contract/FinanceContractApplicationController.java"));
        assertTrue(!controller.contains("@PostMapping(\"/on-approval-outcome\")")
                        && !controller.contains("@PostMapping(\"/on-approval-outcome\")"),
                "HTTP on-approval-outcome must be removed (CS-F1)");
        assertTrue(controller.contains("taskId") || controller.contains("record-seal"),
                "record APIs remain");
    }

    @Test
    void menuSqlShouldGrantBsAndFa() throws Exception {
        String sql = Files.readString(findRepositoryRoot().resolve(
                "sql/mysql/finance_contract_application_menu_phase1.sql"));
        assertTrue(sql.contains("finance/contract-application/index"));
        assertTrue(sql.contains("finance:contract-application:query"));
        assertTrue(sql.contains("finance:contract-application:create"));
        assertTrue(sql.contains("business_staff"));
        assertTrue(sql.contains("finance_admin"));
    }

    @Test
    void menuSqlShouldAlignExecPermsWithBpmCandidateRoles() throws Exception {
        String sql = Files.readString(findRepositoryRoot().resolve(
                "sql/mysql/finance_contract_application_menu_phase1.sql"));
        assertTrue(sql.contains("finance:contract-application:record-seal"));
        assertTrue(sql.contains("finance:contract-application:record-mail"));
        assertTrue(sql.contains("contract_seal_admin"));
        assertTrue(sql.contains("contract_mail"));
        String controller = Files.readString(findRepositoryRoot().resolve(
                "yudao-module-finance/yudao-module-finance-server/src/main/java/cn/iocoder/yudao/module/finance/controller/admin/contract/FinanceContractApplicationController.java"));
        assertTrue(controller.contains("finance:contract-application:record-seal"));
        assertTrue(controller.contains("finance:contract-application:record-mail"));
        assertTrue(!controller.contains(
                "hasPermission('finance:contract-application:update')\")\n    public CommonResult<Boolean> recordSeal"));
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("repo root not found");
        }
        return current;
    }
}
