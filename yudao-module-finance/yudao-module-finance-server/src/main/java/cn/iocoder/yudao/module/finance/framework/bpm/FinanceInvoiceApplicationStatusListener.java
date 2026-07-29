package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationService;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 开票申请流程状态 Listener — <b>辅路径 only</b>。
 *
 * <p>依赖 {@code BpmNotificationManager} / 本地事件；默认 async 且
 * {@code BpmLocalEventNotificationHandler} 吞异常，<b>不可</b>作为占用/可认领主路径。
 * 主路径请使用 {@link FinanceInvoiceApprovalOutcomeDelegate} 同步挂载。
 *
 * <p>本 Listener 调用 {@code onApprovalOutcome} 时不吞异常（finance 侧），但上游异步链路仍可能吞掉。
 */
@Component
@Slf4j
public class FinanceInvoiceApplicationStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private FinanceInvoiceApplicationService invoiceApplicationService;

    @Override
    protected String getProcessDefinitionKey() {
        return FinanceInvoiceApplicationServiceImpl.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        if (event == null || event.getProcessInstanceInfo() == null) {
            return;
        }
        String businessKey = event.getBusinessKey();
        if (StrUtil.isBlank(businessKey)) {
            log.warn("[onEvent][finance_invoice_apply] blank businessKey, skip");
            return;
        }
        Integer processStatus = event.getProcessInstanceInfo().getStatus();
        String outcome = FinanceInvoiceApprovalOutcomeDelegate.mapProcessStatusToOutcome(processStatus);
        if (outcome == null) {
            // 运行中等非终态：忽略
            log.debug("[onEvent][finance_invoice_apply] non-terminal status={}, skip", processStatus);
            return;
        }
        Long appId = Long.parseLong(businessKey.trim());
        log.info("[onEvent][AUX][appId({}) status={} -> {}] auxiliary path only",
                appId, processStatus, outcome);
        // 不吞：finance 侧失败向上抛；注意上游 BpmLocalEventNotificationHandler 仍可能 catch
        invoiceApplicationService.onApprovalOutcome(appId, outcome);
    }

}
