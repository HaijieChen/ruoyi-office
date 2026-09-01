package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import org.springframework.stereotype.Component;

/**
 * 无票费用报销流程状态辅路径（processKey 独立，落账复用报销底座）。
 */
@Component
public class FinanceExpenseNoInvoiceApplicationStatusListener extends FinanceExpenseApplicationStatusListener {

    @Override
    protected String getProcessDefinitionKey() {
        return FinanceExpenseReimbursementService.PROCESS_KEY_NO_INVOICE;
    }
}
