package cn.iocoder.yudao.module.finance.framework.bpm;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 出纳节点 complete：允许直接通过，不再要求支付凭证。
 * 已部署 BPMN 仍可能挂此 listener，须保持 bean 名并空操作。
 */
@Component("financePaymentCashierCompleteGuardListener")
@Slf4j
public class FinancePaymentCashierCompleteGuardListener implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        if (delegateTask == null || !EVENTNAME_COMPLETE.equals(delegateTask.getEventName())) {
            return;
        }
        log.info("[notify][payment cashier complete allowed without evidence] task={}",
                delegateTask.getTaskDefinitionKey());
    }
}
