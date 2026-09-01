package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 付款申请终态同步落账（PAY-R8：主路径）。
 * <p>审批通过结束 → WAIT_PAY（已通过待支付）；支付在列表登记后才 PAID。
 * <p>BPMN endEvent 在同一流程事务内调用；reject/cancel 经 moveTaskToEnd → end 也会走到此处。
 * 辅路径见 {@link FinancePaymentApplicationStatusListener}（async 通知，可失败；补偿用 replay API）。
 * <pre>${financePaymentApprovalOutcomeDelegate}</pre>
 */
@Component("financePaymentApprovalOutcomeDelegate")
@Slf4j
public class FinancePaymentApprovalOutcomeDelegate implements JavaDelegate, ExecutionListener {

    public static final String PROCESS_STATUS_VARIABLE = "PROCESS_STATUS";

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;

    @Override
    public void execute(DelegateExecution execution) {
        applyOutcome(execution);
    }

    @Override
    public void notify(DelegateExecution execution) {
        applyOutcome(execution);
    }

    private void applyOutcome(DelegateExecution execution) {
        String businessKey = execution.getProcessInstanceBusinessKey();
        if (StrUtil.isBlank(businessKey)) {
            throw new IllegalStateException("financePaymentApprovalOutcomeDelegate: blank businessKey");
        }
        Long appId = Long.parseLong(businessKey.trim());
        Object statusVar = execution.getVariable(PROCESS_STATUS_VARIABLE);
        Integer processStatus = toInteger(statusVar);
        String outcome = mapProcessStatusToOutcome(processStatus);
        if (outcome == null) {
            throw new IllegalStateException(
                    "financePaymentApprovalOutcomeDelegate: cannot map PROCESS_STATUS=" + statusVar);
        }
        log.info("[applyOutcome][payment appId({}) PROCESS_STATUS={} -> {}]", appId, processStatus, outcome);
        paymentApplicationService.onApprovalOutcome(appId, outcome, execution.getProcessInstanceId());
    }

    public static String mapProcessStatusToOutcome(Integer processStatus) {
        if (processStatus == null
                || BpmProcessInstanceStatusEnum.RUNNING.getStatus().equals(processStatus)) {
            // 审批流结束且尚未支付：已通过待支付
            return FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            return FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.REJECT.getStatus().equals(processStatus)) {
            return FinancePaymentApplicationStatusEnum.REJECTED.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.WITHDRAW.getStatus().equals(processStatus)) {
            return FinancePaymentApplicationStatusEnum.CANCELLED.getStatus();
        }
        return null;
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
