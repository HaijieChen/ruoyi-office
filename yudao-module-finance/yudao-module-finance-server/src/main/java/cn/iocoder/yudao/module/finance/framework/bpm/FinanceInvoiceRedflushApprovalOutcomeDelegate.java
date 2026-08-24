package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceRedflushService;
import jakarta.annotation.Resource;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 红冲审批结果。只改红冲单状态并解锁原单，绝不调用开票 occupy 释占。
 * BPM 挂载：${financeInvoiceRedFlushApprovalOutcomeDelegate}
 */
@Component("financeInvoiceRedFlushApprovalOutcomeDelegate")
public class FinanceInvoiceRedflushApprovalOutcomeDelegate implements JavaDelegate, ExecutionListener {

    public static final String PROCESS_STATUS_VARIABLE = "PROCESS_STATUS";

    @Resource
    private FinanceInvoiceRedflushService redflushService;

    @Override
    public void execute(DelegateExecution execution) {
        apply(execution);
    }

    @Override
    public void notify(DelegateExecution execution) {
        apply(execution);
    }

    private void apply(DelegateExecution execution) {
        String businessKey = execution.getProcessInstanceBusinessKey();
        if (StrUtil.isBlank(businessKey)) {
            throw new IllegalStateException("redflush outcome: blank businessKey");
        }
        Object status = execution.getVariable(PROCESS_STATUS_VARIABLE);
        String outcome = FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus();
        if (status != null) {
            int code = status instanceof Number ? ((Number) status).intValue() : Integer.parseInt(status.toString());
            if (code == BpmTaskStatusEnum.REJECT.getStatus()) {
                outcome = FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus();
            } else if (code == BpmTaskStatusEnum.CANCEL.getStatus() || code == 10) {
                outcome = FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus();
            }
        }
        redflushService.onApprovalOutcome(Long.valueOf(businessKey), outcome);
    }
}
