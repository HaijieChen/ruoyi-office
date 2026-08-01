package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 合同签约审批结果 — Flowable 同步落账主路径（对齐开票 Delegate）。
 *
 * <pre>${financeContractApprovalOutcomeDelegate}</pre>
 */
@Component("financeContractApprovalOutcomeDelegate")
@Slf4j
public class FinanceContractApprovalOutcomeDelegate implements JavaDelegate, ExecutionListener {

    public static final String PROCESS_STATUS_VARIABLE = "PROCESS_STATUS";

    @Resource
    private FinanceContractApplicationService contractApplicationService;

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
            throw new IllegalStateException(
                    "financeContractApprovalOutcomeDelegate: process businessKey is blank");
        }
        Long appId;
        try {
            appId = Long.parseLong(businessKey.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "financeContractApprovalOutcomeDelegate: invalid businessKey=" + businessKey, ex);
        }

        Object statusVar = execution.getVariable(PROCESS_STATUS_VARIABLE);
        Integer processStatus = toInteger(statusVar);
        String outcome = mapProcessStatusToOutcome(processStatus);
        if (outcome == null) {
            throw new IllegalStateException(
                    "financeContractApprovalOutcomeDelegate: cannot map PROCESS_STATUS="
                            + statusVar + " to contract approval outcome");
        }

        log.info("[applyOutcome][contract appId({}) processInstanceId({}) PROCESS_STATUS={} -> {}]",
                appId, execution.getProcessInstanceId(), processStatus, outcome);
        contractApplicationService.onApprovalOutcome(appId, outcome);
    }

    public static String mapProcessStatusToOutcome(Integer processStatus) {
        if (processStatus == null
                || BpmProcessInstanceStatusEnum.RUNNING.getStatus().equals(processStatus)) {
            return FinanceContractApprovalStatusEnum.APPROVED.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            return FinanceContractApprovalStatusEnum.APPROVED.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.REJECT.getStatus().equals(processStatus)) {
            return FinanceContractApprovalStatusEnum.REJECTED.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.WITHDRAW.getStatus().equals(processStatus)) {
            return FinanceContractApprovalStatusEnum.CANCELLED.getStatus();
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
