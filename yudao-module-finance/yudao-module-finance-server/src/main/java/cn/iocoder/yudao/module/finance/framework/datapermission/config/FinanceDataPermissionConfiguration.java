package cn.iocoder.yudao.module.finance.framework.datapermission.config;

import cn.iocoder.yudao.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.feepayment.FinanceHandlingFeePaymentDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 财务列表：角色数据范围（全部/指定部门/本部门/本部门及以下/仅本人/任职公司）。
 */
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

            rule.addDeptColumn(FinanceBusinessOrderDO.class, "entity_company_dept_id");
            rule.addDeptColumn(FinanceBusinessOrderDO.class, "applicant_dept_id");
            rule.addUserColumn(FinanceBusinessOrderDO.class, "applicant_user_id");

            rule.addDeptColumn(FinanceReceiptDO.class, "entity_company_dept_id");
            rule.addUserColumn(FinanceReceiptDO.class, "importer_id");

            rule.addDeptColumn(FinanceHandlingFeePaymentDO.class, "entity_company_dept_id");

            rule.addDeptColumn(FinanceCompanyBankAccountDO.class, "entity_company_dept_id");
        };
    }
}
