package cn.iocoder.yudao.module.finance.service.payment;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-73 BPM-1：付款 BPMN 静态契约 + 角色候选人迁移脚本门禁。
 */
class FinancePaymentBpmnAndRoleContractTest {

    @Test
    void bpmnMustContainFinanceAndCashierRoleCandidates() throws Exception {
        Path root = findRepositoryRoot();
        Path bpmn = root.resolve("sql/mysql/bpmn/finance_payment_apply.bpmn20.xml");
        assertTrue(Files.exists(bpmn), "payment BPMN seed must exist");
        String xml = Files.readString(bpmn);

        assertTrue(xml.contains("id=\"finance_payment_apply\"") || xml.contains("id='finance_payment_apply'"));
        assertTrue(xml.contains("taskFinance"), "must include taskFinance");
        assertTrue(xml.contains("taskCashier"), "must include taskCashier");
        assertTrue(xml.contains("payment_finance"), "finance candidate role");
        assertTrue(xml.contains("payment_cashier"), "cashier candidate role");
        // 金额网关两条支路最终汇入财务
        assertTrue(xml.contains("sourceRef=\"gatewayAmount\"") || xml.contains("gatewayAmount"));
        assertTrue(xml.contains("sourceRef=\"taskBizHead\"") || xml.contains("taskBizHead"));
        assertTrue(xml.contains("targetRef=\"taskFinance\""));
        assertTrue(xml.contains("sourceRef=\"taskFinance\"") && xml.contains("targetRef=\"taskCashier\""),
                "taskFinance → taskCashier");
        assertTrue(xml.toLowerCase(Locale.ROOT).contains("bpmndiagram"), "must include diagram DI");
    }

    @Test
    void roleCandidateMigrationMustExistAndBindTestUser() throws Exception {
        Path root = findRepositoryRoot();
        Path sql = root.resolve("sql/mysql/finance_payment_role_candidates_exp73.sql");
        assertTrue(Files.exists(sql), "role candidate SQL must exist");
        String text = Files.readString(sql);
        assertTrue(text.contains("payment_finance"));
        assertTrue(text.contains("payment_cashier"));
        assertTrue(text.contains("financeadminuser"), "test env binds financeadminuser");
        assertTrue(text.contains("finance:payment-application:query"));
        assertTrue(text.contains("finance:payment-application:record-pay"));
        // EXP-73 ENV-1：最小 BPM 待办权限必须闭环，避免精简库 todo 403
        assertTrue(text.contains("bpm:task:query"), "must grant bpm:task:query");
        assertTrue(text.contains("bpm:task:update"), "must grant bpm:task:update");
        assertTrue(text.contains("bpm:process-instance:query"),
                "must grant bpm:process-instance:query for todo/process page");
        // 禁止默认把 finance_admin 整角色当候选人（脚本应保留分离说明）
        assertTrue(text.contains("finance_admin") && text.toLowerCase(Locale.ROOT).contains("分离")
                        || text.contains("禁止把全体 finance_admin"),
                "must document separation from finance_admin");
        // 不得把 finance_admin 整角色菜单批量赋给候选人（最小集，非全量 admin）
        assertFalse(text.contains("r.`code` IN ('finance_admin'")
                        || text.contains("code` = 'finance_admin' AND m.`permission`"),
                "must not bulk-assign finance_admin menus as payment candidates");
    }

    @Test
    void entityCompanyAndCurrencyMigrationsExist() throws Exception {
        Path root = findRepositoryRoot();
        Path pay = root.resolve("sql/mysql/finance_payment_entity_company_currency_exp73.sql");
        Path org = root.resolve("sql/mysql/system_dept_functional_currency_exp73.sql");
        assertTrue(Files.exists(pay));
        assertTrue(Files.exists(org));
        String paySql = Files.readString(pay);
        assertTrue(paySql.contains("entity_company_dept_id"));
        assertTrue(paySql.contains("entity_company_name"));
        String orgSql = Files.readString(org);
        assertTrue(orgSql.contains("functional_currency"));
        assertTrue(orgSql.contains("勿盲填") || orgSql.contains("不建议默认全量 CNY")
                || orgSql.contains("勿盲填全 CNY") || orgSql.contains("不盲填"));
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
