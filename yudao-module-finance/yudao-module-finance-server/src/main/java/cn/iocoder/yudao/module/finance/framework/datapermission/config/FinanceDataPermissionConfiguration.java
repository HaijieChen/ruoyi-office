package cn.iocoder.yudao.module.finance.framework.datapermission.config;

import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FinanceDataPermissionConfiguration {

    @Bean
    public DeptDataPermissionRuleCustomizer financeDeptDataPermissionRuleCustomizer() {
        return rule -> {
            rule.addDeptColumn(FinanceContractApplicationDO.class, "entity_company_dept_id");
            rule.addDeptColumn(FinanceContractApplicationDO.class, "applicant_dept_id");
            rule.addUserColumn(FinanceContractApplicationDO.class, "applicant_user_id");

            rule.addDeptColumn(FinanceExpenseReimbursementDO.class, "entity_company_dept_id");
            rule.addDeptColumn(FinanceExpenseReimbursementDO.class, "applicant_dept_id");
            rule.addUserColumn(FinanceExpenseReimbursementDO.class, "applicant_user_id");

            rule.addDeptColumn(FinancePaymentApplicationDO.class, "entity_company_dept_id");
            rule.addDeptColumn(FinancePaymentApplicationDO.class, "applicant_dept_id");
            rule.addUserColumn(FinancePaymentApplicationDO.class, "applicant_user_id");

            rule.addDeptColumn(FinanceInvoiceApplicationDO.class, "invoice_company_dept_id");
            rule.addUserColumn(FinanceInvoiceApplicationDO.class, "applicant_user_id");
        };
    }
}
