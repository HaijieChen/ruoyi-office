package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 付款申请 UserTask create → 回写节点标签；出纳节点标 WAIT_PAY。
 */
@Component("financePaymentTaskNodeLabelListener")
@Slf4j
public class FinancePaymentTaskNodeLabelListener implements TaskListener {

    private static final Map<String, String[]> NODE_MAP = Map.of(
            "taskDeptHead", new String[]{"dept_head", "待部门负责人"},
            "taskBizHead", new String[]{"biz_head", "待业务负责人"},
            "taskFinance", new String[]{"finance", "待财务主管"},
            "taskCashier", new String[]{"cashier", "待出纳"}
    );

    @Resource
    private FinancePaymentApplicationService paymentApplicationService;

    @Override
    public void notify(DelegateTask delegateTask) {
        if (delegateTask == null || !EVENTNAME_CREATE.equals(delegateTask.getEventName())) {
            return;
        }
        Long appId = resolveAppId(delegateTask);
        if (appId == null) {
            log.warn("[notify] cannot resolve payment appId for task {}",
                    delegateTask.getTaskDefinitionKey());
            return;
        }
        String[] mapped = NODE_MAP.get(delegateTask.getTaskDefinitionKey());
        String key = mapped != null ? mapped[0] : delegateTask.getTaskDefinitionKey();
        String name = mapped != null ? mapped[1] : delegateTask.getName();
        paymentApplicationService.updateCurrentNode(appId, key, name);
        log.info("[notify][payment appId({}) node={}/{}]", appId, key, name);
    }

    private static Long resolveAppId(DelegateTask delegateTask) {
        Object var = delegateTask.getVariable("paymentApplicationId");
        if (var instanceof Number n) {
            return n.longValue();
        }
        if (var != null && StrUtil.isNotBlank(var.toString())) {
            try {
                return Long.parseLong(var.toString().trim());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return null;
    }
}
