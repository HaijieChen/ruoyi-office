package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * 用印/归档/邮寄 complete 守卫：空资料无法 complete；并回写权威 needMail。
 *
 * <pre>${financeContractExecCompleteGuardListener}</pre>
 * event = complete
 */
@Component("financeContractExecCompleteGuardListener")
@Slf4j
public class FinanceContractExecCompleteGuardListener implements TaskListener {

    @Resource
    private FinanceContractApplicationService contractApplicationService;

    @Override
    public void notify(DelegateTask delegateTask) {
        if (delegateTask == null || !EVENTNAME_COMPLETE.equals(delegateTask.getEventName())) {
            return;
        }
        String taskKey = delegateTask.getTaskDefinitionKey();
        Long appId = resolveAppId(delegateTask);
        if (appId == null) {
            throw new IllegalStateException(
                    "financeContractExecCompleteGuardListener: cannot resolve contractApplicationId for "
                            + taskKey);
        }
        // 用印/归档完成时从台账回写 needMail，堵住通用 approve 改 variables 跳过邮寄
        if (FinanceContractApplicationServiceImpl.TASK_ARCHIVE.equals(taskKey)
                || FinanceContractApplicationServiceImpl.TASK_SEAL.equals(taskKey)) {
            boolean needMail = contractApplicationService.resolveNeedMailFromLedger(appId);
            delegateTask.setVariable("needMail", needMail);
        }
        contractApplicationService.assertExecutionEvidenceForComplete(appId, taskKey);
        log.info("[notify][contract exec guard ok] appId={} task={}", appId, taskKey);
    }

    private static Long resolveAppId(DelegateTask delegateTask) {
        Object var = delegateTask.getVariable("contractApplicationId");
        if (var instanceof Number n) {
            return n.longValue();
        }
        if (var != null && StrUtil.isNotBlank(var.toString())) {
            try {
                return Long.parseLong(var.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
