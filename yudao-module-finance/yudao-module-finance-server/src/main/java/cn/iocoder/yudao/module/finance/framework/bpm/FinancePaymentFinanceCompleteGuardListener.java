package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 财务节点 complete 兼容入口：普通付款不要求科目，薪资与税款保留科目非空校验。
 *
 * <pre>${financePaymentFinanceCompleteGuardListener}</pre>
 * event = complete on taskFinance
 */
@Component("financePaymentFinanceCompleteGuardListener")
@Slf4j
public class FinancePaymentFinanceCompleteGuardListener implements TaskListener {

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;

    @Override
    public void notify(DelegateTask delegateTask) {
        if (delegateTask == null || !EVENTNAME_COMPLETE.equals(delegateTask.getEventName())) {
            return;
        }
        if (!FinancePaymentApplicationService.TASK_FINANCE.equals(delegateTask.getTaskDefinitionKey())) {
            return;
        }
        Long appId = resolveAppId(delegateTask);
        if (appId == null) {
            throw new IllegalStateException(
                    "financePaymentFinanceCompleteGuardListener: cannot resolve paymentApplicationId");
        }
        paymentApplicationService.assertFinanceSubjectForComplete(appId);
        log.info("[notify][payment finance subject guard ok] appId={}", appId);
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
