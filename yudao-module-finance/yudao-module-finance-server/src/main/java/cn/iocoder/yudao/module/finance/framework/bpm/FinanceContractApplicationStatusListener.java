package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 合同签约流程状态 — 辅路径 Listener。
 * <p>PAY-R11：与付款相同的 PI 绑定 + tenant&gt;0 fail-closed。
 */
@Component
@Slf4j
public class FinanceContractApplicationStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private FinanceContractApplicationService contractApplicationService;
    @Resource
    private FinanceContractApplicationMapper contractApplicationMapper;

    @Override
    protected String getProcessDefinitionKey() {
        return FinanceContractApplicationServiceImpl.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        if (event == null || event.getProcessInstanceInfo() == null) {
            return;
        }
        String businessKey = event.getBusinessKey();
        if (StrUtil.isBlank(businessKey)) {
            log.warn("[onEvent][finance_contract_sign] blank businessKey, skip");
            return;
        }
        Integer processStatus = event.getProcessInstanceInfo().getStatus();
        String outcome = FinanceContractApprovalOutcomeDelegate.mapProcessStatusToOutcome(processStatus);
        if (outcome == null) {
            log.debug("[onEvent][finance_contract_sign] non-terminal status={}, skip", processStatus);
            return;
        }
        Long appId = Long.parseLong(businessKey.trim());
        String processInstanceId = event.getProcessInstanceId();
        log.info("[onEvent][AUX][contract appId({}) processInstanceId({}) status={} -> {}]",
                appId, processInstanceId, processStatus, outcome);
        Runnable write = () -> contractApplicationService.onApprovalOutcome(appId, outcome, processInstanceId);

        try {
            FinanceBpmTenantSupport.runLedgerWrite(event, "contract", appId,
                    () -> resolveTenantFromRow(appId, processInstanceId),
                    write);
        } catch (RuntimeException ex) {
            log.error("[onEvent][contract status sync failed] appId={} pi={} outcome={}",
                    appId, processInstanceId, outcome, ex);
            throw ex;
        }
    }

    private Long resolveTenantFromRow(Long appId, String processInstanceId) {
        FinanceContractApplicationDO row = contractApplicationMapper.selectById(appId);
        if (row == null) {
            log.warn("[resolveTenantFromRow][contract] app not found appId={}", appId);
            return null;
        }
        return FinanceBpmTenantSupport.resolveTenantIfProcessBound(
                row.getTenantId(), processInstanceId, row.getProcessInstanceId(), "contract", appId);
    }
}
