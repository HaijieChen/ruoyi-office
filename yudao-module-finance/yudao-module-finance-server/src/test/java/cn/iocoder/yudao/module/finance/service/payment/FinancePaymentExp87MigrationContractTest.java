package cn.iocoder.yudao.module.finance.service.payment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** EXP-87：迁移脚本与硬约束契约 */
class FinancePaymentExp87MigrationContractTest {

    @Test
    void companyBankAccountSqlMustFkToOrgCompanyOnly() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/finance_company_bank_account_exp87.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);
        assertTrue(text.contains("finance_company_bank_account"));
        assertTrue(text.contains("entity_company_dept_id"));
        // 禁止再建公司主体表：不得出现独立 company_master 一类表
        assertFalse(text.toLowerCase().contains("create table if not exists `finance_company`"));
        assertFalse(text.contains("company_name"));
        assertTrue(text.contains("finance:company-bank-account:simple-list"));
    }

    @Test
    void payLineAndSalaryTaxSqlExist() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/finance_payment_pay_line_exp87.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);
        assertTrue(text.contains("finance_payment_pay_line"));
        assertTrue(text.contains("account_name_snapshot"));
        assertTrue(text.contains("finance_payment_salary_line"));
        assertTrue(text.contains("finance_payment_tax_line"));
        assertTrue(text.contains("application_kind"));
        assertTrue(text.contains("social_insurance_amount"));
    }

    @Test
    void salaryHousingFundSqlIsIdempotentAddColumn() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/finance_payment_salary_line_housing_fund.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);
        assertTrue(text.contains("housing_fund_amount"));
        assertTrue(text.contains("information_schema"));
        assertTrue(text.contains("NOT NULL DEFAULT 0.00"));
        assertFalse(text.contains("CREATE TABLE"));
    }

    @Test
    void salaryTaxBpmnReusePaymentDelegates() throws Exception {
        Path salary = findRoot().resolve("sql/mysql/bpmn/finance_salary_payment_apply.bpmn20.xml");
        Path tax = findRoot().resolve("sql/mysql/bpmn/finance_tax_payment_apply.bpmn20.xml");
        assertTrue(Files.exists(salary));
        assertTrue(Files.exists(tax));
        String s = Files.readString(salary);
        String t = Files.readString(tax);
        assertTrue(s.contains("id=\"finance_salary_payment_apply\""));
        assertTrue(t.contains("id=\"finance_tax_payment_apply\""));
        assertTrue(s.contains("financePaymentApprovalOutcomeDelegate"));
        assertTrue(t.contains("financePaymentCashierCompleteGuardListener"));
        assertTrue(s.contains("taskCashier"));
    }

    @Test
    void formViewPathAndPshellCoverSalaryTax() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/finance_salary_tax_bpm_form_view_path_exp87.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);
        assertTrue(text.contains("/finance/salary-payment/detail/index"));
        assertTrue(text.contains("/finance/tax-payment/detail/index"));
        Path constants = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/bpm/processInstance/constants.ts");
        String ts = Files.readString(constants);
        assertTrue(ts.contains("salary-payment/detail/index"));
        assertTrue(ts.contains("tax-payment/detail/index"));
    }

    @Test
    void payR19CancelGuardCoversSalaryTaxKeys() throws Exception {
        Path java = findRoot().resolve(
                "yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmProcessInstanceServiceImpl.java");
        String text = Files.readString(java);
        assertTrue(text.contains("finance_salary_payment_apply"));
        assertTrue(text.contains("finance_tax_payment_apply"));
        assertTrue(text.contains("PAYMENT_DOMAIN_CANCEL_PROCESS_KEYS"));
    }

    @Test
    void detailCashierRequiresCompanyBankAccountId() throws Exception {
        Path cashier = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/finance/payment-application/detail/cashier.vue");
        Path detail = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/finance/payment-application/detail/index.vue");
        String cashierText = Files.readString(cashier);
        String text = Files.readString(detail);
        assertTrue(cashierText.contains("mode=\"cashier\""));
        assertTrue(text.contains("companyBankAccountId"));
        assertTrue(text.contains("getCompanyBankAccountSimpleList"));
        assertTrue(text.contains("payAmount"));
    }

    @Test
    void salaryTaxNotInFrontendCreateShellButCatalogRedirectAndBackendDenyGeneric() throws Exception {
        Path fe = findRoot().resolve(
                "ruoyi-office-vben/apps/web-antd/src/views/bpm/processInstance/create/embed-registry.ts");
        String ts = Files.readString(fe);
        // 统一发起壳内嵌业务表单（与合同签约一致）
        assertTrue(ts.contains("finance_salary_payment_apply: ()"));
        assertTrue(ts.contains("finance_tax_payment_apply: ()"));
        assertTrue(ts.contains("salary-payment/modules/form-body.vue"));
        assertTrue(ts.contains("tax-payment/modules/form-body.vue"));
        assertFalse(ts.contains("finance_salary_payment_apply: '/finance/salary-payment'"));
        assertFalse(ts.contains("finance_tax_payment_apply: '/finance/tax-payment'"));
        Path be = findRoot().resolve(
                "yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmEmbedProcessStartPermissionRegistry.java");
        String java = Files.readString(be);
        assertTrue(java.contains("\"finance_salary_payment_apply\""));
        assertTrue(java.contains("isCreateShellEmbedAllowed"));
        assertTrue(java.contains("catalogRedirectPath"));
        Path elig = findRoot().resolve(
                "yudao-module-bpm/yudao-module-bpm-server/src/main/java/cn/iocoder/yudao/module/bpm/service/definition/BpmProcessStartEligibilityServiceImpl.java");
        String e = Files.readString(elig);
        assertTrue(e.contains("isMenuOnlyPaymentProcess") || e.contains("finance_salary_payment_apply"));
        // 通用直启硬拒绝仍在
        assertTrue(e.contains("validateStartOrThrow") && e.contains("!trustedBusinessStart"));
    }

    @Test
    void accessLogSanitizesAccountNo() throws Exception {
        Path filter = findRoot().resolve(
                "yudao-framework/yudao-spring-boot-starter-web/src/main/java/cn/iocoder/yudao/framework/apilog/core/filter/ApiAccessLogFilter.java");
        String text = Files.readString(filter);
        assertTrue(text.contains("accountNo"));
        assertTrue(text.contains("payeeBankAccount"));
    }

    @Test
    void forwardOnlyRunbookExists() throws Exception {
        Path doc = findRoot().resolve("sql/mysql/EXP87_FORWARD_ONLY_RUNBOOK.md");
        assertTrue(Files.exists(doc));
        String text = Files.readString(doc);
        assertTrue(text.contains("forward-only") || text.contains("Forward-only") || text.contains("仅向前"));
        assertTrue(text.contains("mysqldump") || text.contains("备份"));
    }

    @Test
    void paymentBpmnNodesHangFinanceAndCashierVuePaths() throws Exception {
        record Case(String file, String financePath, String cashierPath) {}
        Case[] cases = {
                new Case("sql/mysql/bpmn/finance_payment_apply.bpmn20.xml",
                        "/finance/payment-application/detail/finance",
                        "/finance/payment-application/detail/cashier"),
                new Case("sql/mysql/bpmn/finance_salary_payment_apply.bpmn20.xml",
                        "/finance/salary-payment/detail/finance",
                        "/finance/salary-payment/detail/cashier"),
                new Case("sql/mysql/bpmn/finance_tax_payment_apply.bpmn20.xml",
                        "/finance/tax-payment/detail/finance",
                        "/finance/tax-payment/detail/cashier"),
        };
        for (Case c : cases) {
            String xml = Files.readString(findRoot().resolve(c.file));
            assertTrue(xml.contains("id=\"taskFinance\"") && xml.contains(c.financePath), c.file);
            assertTrue(xml.contains("id=\"taskCashier\"") && xml.contains(c.cashierPath), c.file);
            assertFalse(xml.contains("id=\"taskDeptHead\"") && xml.substring(xml.indexOf("id=\"taskDeptHead\""),
                    xml.indexOf("id=\"taskFinance\"")).contains("formCustomViewPath"), c.file);
        }
    }

    private static Path findRoot() {
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
