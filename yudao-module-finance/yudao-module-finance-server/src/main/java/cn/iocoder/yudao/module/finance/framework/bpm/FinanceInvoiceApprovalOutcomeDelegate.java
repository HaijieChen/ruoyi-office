package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 开票申请审批结果 — Flowable <b>同步</b> JavaDelegate（主路径，D-T3）。
 *
 * <p>BPM 模型挂载方式（delegateExpression）：
 * <pre>
 * ${financeInvoiceApprovalOutcomeDelegate}
 * </pre>
 * 建议挂在流程 end 事件或审批通过/驳回出口的 ExecutionListener / ServiceTask。
 *
 * <p><b>失败必须抛出</b>，使引擎事务回滚；禁止吞异常。
 * 异步 {@code BpmNotificationManager} 仅作辅路径，不可替代本 Delegate。
 */
@Component("financeInvoiceApprovalOutcomeDelegate")
@Slf4j
public class FinanceInvoiceApprovalOutcomeDelegate implements JavaDelegate {

    /**
     * 与 BPM 引擎变量一致（见 BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS）
     */
    public static final String PROCESS_STATUS_VARIABLE = "PROCESS_STATUS";

    @Resource
    private FinanceInvoiceApplicationService invoiceApplicationService;

    @Override
    public void execute(DelegateExecution execution) {
        String businessKey = execution.getProcessInstanceBusinessKey();
        if (StrUtil.isBlank(businessKey)) {
            throw new IllegalStateException(
                    "financeInvoiceApprovalOutcomeDelegate: process businessKey is blank");
        }
        Long appId;
        try {
            appId = Long.parseLong(businessKey.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "financeInvoiceApprovalOutcomeDelegate: invalid businessKey=" + businessKey, ex);
        }

        Object statusVar = execution.getVariable(PROCESS_STATUS_VARIABLE);
        Integer processStatus = toInteger(statusVar);
        String outcome = mapProcessStatusToOutcome(processStatus);
        if (outcome == null) {
            throw new IllegalStateException(
                    "financeInvoiceApprovalOutcomeDelegate: cannot map PROCESS_STATUS="
                            + statusVar + " to invoice approval outcome");
        }

        log.info("[execute][appId({}) processInstanceId({}) PROCESS_STATUS={} -> {}]",
                appId, execution.getProcessInstanceId(), processStatus, outcome);
        // 不 catch：ServiceException / 任意失败向上抛，阻断引擎事务
        invoiceApplicationService.onApprovalOutcome(appId, outcome);
    }

    /**
     * BpmTaskStatusEnum → FinanceInvoiceApprovalStatusEnum 字符串。
     */
    public static String mapProcessStatusToOutcome(Integer processStatus) {
        if (processStatus == null) {
            return null;
        }
        if (BpmTaskStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            return FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus();
        }
        if (BpmTaskStatusEnum.REJECT.getStatus().equals(processStatus)) {
            return FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus();
        }
        if (BpmTaskStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.WITHDRAW.getStatus().equals(processStatus)) {
            return FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus();
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
        if (value instanceof String s && StrUtil.isNotBlank(s)) {
            return Integer.valueOf(s.trim());
        }
        throw new IllegalStateException(
                "financeInvoiceApprovalOutcomeDelegate: PROCESS_STATUS type unsupported: "
                        + value.getClass().getName());
    }

}
