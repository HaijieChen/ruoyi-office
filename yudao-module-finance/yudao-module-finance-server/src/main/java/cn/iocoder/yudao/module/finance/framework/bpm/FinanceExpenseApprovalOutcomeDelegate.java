package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 费用报销终态同步落账（主路径）。
 * <p>BPMN endEvent 在同一流程事务内调用；reject/cancel 经 moveTaskToEnd → end 也会走到此处。
 * 辅路径见 {@link FinanceExpenseApplicationStatusListener}。
 * APPROVE/RUNNING 不改占用（通过仍走 approve/recordPay）。
 * <pre>${financeExpenseApprovalOutcomeDelegate}</pre>
 */
@Component("financeExpenseApprovalOutcomeDelegate")
@Slf4j
public class FinanceExpenseApprovalOutcomeDelegate implements JavaDelegate, ExecutionListener {

    public static final String PROCESS_STATUS_VARIABLE = "PROCESS_STATUS";

    @Resource
    private FinanceExpenseReimbursementService expenseReimbursementService;

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
            throw new IllegalStateException("financeExpenseApprovalOutcomeDelegate: blank businessKey");
        }
        String outcome = mapProcessStatusToOutcome(toInteger(execution.getVariable(PROCESS_STATUS_VARIABLE)));
        if (outcome == null) {
            return;
        }
        Long appId = Long.parseLong(businessKey.trim());
        log.info("[applyOutcome][expense appId({}) -> {}]", appId, outcome);
        expenseReimbursementService.onApprovalOutcome(appId, outcome, execution.getProcessInstanceId());
    }

    public static String mapProcessStatusToOutcome(Integer processStatus) {
        if (processStatus == null
                || BpmProcessInstanceStatusEnum.RUNNING.getStatus().equals(processStatus)
                || BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            return null;
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.REJECT.getStatus().equals(processStatus)) {
            return FinanceExpenseReimbursementDO.STATUS_REJECTED;
        }
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.WITHDRAW.getStatus().equals(processStatus)) {
            return FinanceExpenseReimbursementDO.STATUS_CANCELLED;
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
