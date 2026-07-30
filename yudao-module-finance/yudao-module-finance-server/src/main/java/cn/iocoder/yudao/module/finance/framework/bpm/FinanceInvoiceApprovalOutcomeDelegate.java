package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 开票申请审批结果 — Flowable <b>同步</b> 落账（主路径，D-T3）。
 *
 * <p>BPM 模型挂载（delegateExpression）：
 * <pre>
 * ${financeInvoiceApprovalOutcomeDelegate}
 * </pre>
 * 建议挂在<strong>结束事件</strong>的 ExecutionListener（event=end），或审批通过/驳回出口的 ServiceTask。
 *
 * <p>与 yudao {@code BpmProcessInstanceServiceImpl#processProcessInstanceCompleted} 对齐：
 * 流程正常结束且 {@code PROCESS_STATUS} 仍为「审批中」(1) 时，视为<strong>审批通过</strong>。
 * 因为引擎在 complete 任务时会先触发 end 监听器，此时状态尚未被改成 APPROVE(2)。
 *
 * <p>驳回/取消会在结束前先写入 REJECT(3)/CANCEL(4)，本类原样映射。
 *
 * <p><b>失败必须抛出</b>，使引擎事务回滚；禁止吞异常。
 */
@Component("financeInvoiceApprovalOutcomeDelegate")
@Slf4j
public class FinanceInvoiceApprovalOutcomeDelegate implements JavaDelegate, ExecutionListener {

    /**
     * 与 BPM 引擎变量一致（见 BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS）
     */
    public static final String PROCESS_STATUS_VARIABLE = "PROCESS_STATUS";

    @Resource
    private FinanceInvoiceApplicationService invoiceApplicationService;

    @Override
    public void execute(DelegateExecution execution) {
        applyOutcome(execution);
    }

    @Override
    public void notify(DelegateExecution execution) {
        // ExecutionListener（end 事件）入口
        applyOutcome(execution);
    }

    private void applyOutcome(DelegateExecution execution) {
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

        log.info("[applyOutcome][appId({}) processInstanceId({}) PROCESS_STATUS={} -> {}]",
                appId, execution.getProcessInstanceId(), processStatus, outcome);
        // 不 catch：ServiceException / 任意失败向上抛，阻断引擎事务
        invoiceApplicationService.onApprovalOutcome(appId, outcome);
    }

    /**
     * 流程实例状态 → 开票审批结果。
     * <p>兼容 {@link BpmProcessInstanceStatusEnum} 与 {@link BpmTaskStatusEnum} 中相同取值。
     */
    public static String mapProcessStatusToOutcome(Integer processStatus) {
        // 未写入或仍为「审批中」：流程已到达结束节点 ⇒ 视为通过
        // （对齐 processProcessInstanceCompleted 的 RUNNING→APPROVE 语义）
        if (processStatus == null
                || BpmProcessInstanceStatusEnum.RUNNING.getStatus().equals(processStatus)) {
            return FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            return FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.REJECT.getStatus().equals(processStatus)) {
            return FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus();
        }
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(processStatus)
                || BpmTaskStatusEnum.CANCEL.getStatus().equals(processStatus)
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
