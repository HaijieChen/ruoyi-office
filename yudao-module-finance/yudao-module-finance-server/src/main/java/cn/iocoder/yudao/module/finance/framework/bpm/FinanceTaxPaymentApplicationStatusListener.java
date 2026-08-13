package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import org.springframework.stereotype.Component;

/**
 * 税金付款流程状态辅路径（processKey 独立，落账复用付款底座）。
 */
@Component
public class FinanceTaxPaymentApplicationStatusListener extends FinancePaymentApplicationStatusListener {

    @Override
    protected String getProcessDefinitionKey() {
        return FinancePaymentApplicationService.PROCESS_KEY_TAX;
    }

}
