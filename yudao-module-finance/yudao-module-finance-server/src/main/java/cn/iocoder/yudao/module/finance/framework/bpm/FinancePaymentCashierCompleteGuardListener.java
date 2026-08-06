package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 出纳节点 complete 守卫：台账须已有支付日+凭证，否则拒绝 complete（F1）。
 *
 * <p>BPMN taskCashier：
 * <pre>${financePaymentCashierCompleteGuardListener}</pre>
 * event = complete
 *
 * <p>正确路径：先 {@code recordPay} 写台账，再 completeTask；
 * 通用 BPM「通过」在无凭证时会在此失败。
 */
@Component("financePaymentCashierCompleteGuardListener")
@Slf4j
public class FinancePaymentCashierCompleteGuardListener implements TaskListener {

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;

    @Override
    public void notify(DelegateTask delegateTask) {
        if (delegateTask == null || !EVENTNAME_COMPLETE.equals(delegateTask.getEventName())) {
            return;
        }
        String taskKey = delegateTask.getTaskDefinitionKey();
        if (!FinancePaymentApplicationService.TASK_CASHIER.equals(taskKey)) {
            return;
        }
        Long appId = resolveAppId(delegateTask);
        if (appId == null) {
            throw new IllegalStateException(
                    "financePaymentCashierCompleteGuardListener: cannot resolve paymentApplicationId");
        }
        paymentApplicationService.assertCashierEvidenceForComplete(appId);
        log.info("[notify][payment cashier guard ok] appId={} task={}", appId, taskKey);
    }

    private static Long resolveAppId(DelegateTask delegateTask) {
        Object var = delegateTask.getVariable("paymentApplicationId");
        if (var instanceof Number n) {
            return n.longValue();
        }
        if (var != null && StrUtil.isNotBlank(var.toString())) {
            try {
                return Long.parseLong(var.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
