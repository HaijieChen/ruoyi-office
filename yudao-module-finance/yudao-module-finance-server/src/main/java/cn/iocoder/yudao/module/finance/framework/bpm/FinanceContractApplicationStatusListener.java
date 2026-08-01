package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 合同签约流程状态 — 辅路径 Listener（主路径用 Delegate）。
 */
@Component
@Slf4j
public class FinanceContractApplicationStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private FinanceContractApplicationService contractApplicationService;

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
        log.info("[onEvent][AUX][contract appId({}) status={} -> {}]", appId, processStatus, outcome);
        contractApplicationService.onApprovalOutcome(appId, outcome);
    }
}
