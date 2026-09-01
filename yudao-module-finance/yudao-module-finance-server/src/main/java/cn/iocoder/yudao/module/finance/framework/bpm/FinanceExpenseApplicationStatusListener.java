package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 费用报销流程状态事件 → 台账头状态。
 * <p>驳回 / 取消 / 撤回写 REJECTED 或 CANCELLED 并释放占用；通过不改占用。
 * 流程图不挂 finance bean。
 */
@Component
@Slf4j
public class FinanceExpenseApplicationStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private FinanceExpenseReimbursementService expenseReimbursementService;
    @Resource
    private FinanceExpenseReimbursementMapper expenseReimbursementMapper;

    @Override
    protected String getProcessDefinitionKey() {
        return FinanceExpenseReimbursementService.PROCESS_KEY;
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
        String outcome = FinanceExpenseProcessStatusMapper.toOutcome(
                event.getProcessInstanceInfo().getStatus());
        if (outcome == null) {
            return;
        }
        Long appId = Long.parseLong(businessKey.trim());
        String processInstanceId = event.getProcessInstanceId();
        Runnable write = () -> expenseReimbursementService.onApprovalOutcome(appId, outcome, processInstanceId);
        try {
            FinanceBpmTenantSupport.runLedgerWrite(event, "expense", appId,
                    () -> resolveTenantFromRow(appId, processInstanceId),
                    write);
        } catch (RuntimeException ex) {
            log.error("[onEvent][expense status sync failed] appId={} pi={} outcome={}",
                    appId, processInstanceId, outcome, ex);
            throw ex;
        }
    }

    private Long resolveTenantFromRow(Long appId, String processInstanceId) {
        FinanceExpenseReimbursementDO row = expenseReimbursementMapper.selectById(appId);
        if (row == null) {
            log.warn("[resolveTenantFromRow][expense] app not found appId={}", appId);
            return null;
        }
        return FinanceBpmTenantSupport.resolveTenantIfProcessBound(
                row.getTenantId(), processInstanceId, row.getProcessInstanceId(), "expense", appId);
    }
}
