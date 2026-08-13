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
