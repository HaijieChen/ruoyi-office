package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 付款流程状态 — <b>辅路径</b> Listener。
 * <p>主路径：BPMN endEvent → {@link FinancePaymentApprovalOutcomeDelegate}。
 * <p>PAY-R11：事件 PI 必填；fallback 要求 eventPi==rowPi 均非空；tenant&gt;0。
 */
@Component
@Slf4j
public class FinancePaymentApplicationStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;
    @Resource
    private FinancePaymentApplicationMapper paymentApplicationMapper;

    @Override
    protected String getProcessDefinitionKey() {
        return FinancePaymentApplicationService.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        if (event == null || event.getProcessInstanceInfo() == null) {
            return;
        }
        String businessKey = event.getBusinessKey();
        if (StrUtil.isBlank(businessKey)) {
            return;
        }
        Integer processStatus = event.getProcessInstanceInfo().getStatus();
        // 流程启动会发 RUNNING；endEvent 才把 RUNNING/null 视为通过。启动中不得写成待支付。
        if (processStatus == null
                || BpmProcessInstanceStatusEnum.RUNNING.getStatus().equals(processStatus)) {
            return;
        }
        String outcome = FinancePaymentApprovalOutcomeDelegate.mapProcessStatusToOutcome(processStatus);
        if (outcome == null) {
            return;
        }
        Long appId = Long.parseLong(businessKey.trim());
        String processInstanceId = event.getProcessInstanceId();
        Runnable write = () -> paymentApplicationService.onApprovalOutcome(appId, outcome, processInstanceId);

        try {
            FinanceBpmTenantSupport.runLedgerWrite(event, "payment", appId,
                    () -> resolveTenantFromRow(appId, processInstanceId),
                    write);
        } catch (RuntimeException ex) {
            log.error("[onEvent][payment status sync failed] appId={} pi={} outcome={}",
                    appId, processInstanceId, outcome, ex);
            throw ex;
        }
    }

    private Long resolveTenantFromRow(Long appId, String processInstanceId) {
        FinancePaymentApplicationDO row = paymentApplicationMapper.selectById(appId);
        if (row == null) {
            log.warn("[resolveTenantFromRow][payment] app not found appId={}", appId);
            return null;
        }
        return FinanceBpmTenantSupport.resolveTenantIfProcessBound(
                row.getTenantId(), processInstanceId, row.getProcessInstanceId(), "payment", appId);
    }
}
