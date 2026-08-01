package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 合同签约 UserTask create → 回写 current_node_key/name（D-T4）。
 *
 * <p>BPMN 各 UserTask 可挂：
 * <pre>${financeContractTaskNodeLabelListener}</pre>
 * event = create（设计器 TaskListener）。
 * 未挂载时也可由运维/实现在模型统一加；本类按 taskDefinitionKey 映射产品节点 key。
 */
@Component("financeContractTaskNodeLabelListener")
@Slf4j
public class FinanceContractTaskNodeLabelListener implements TaskListener {

    private static final Map<String, String[]> NODE_MAP = Map.of(
            "taskBizLead", new String[]{"biz_lead", "待业务主管"},
            "taskLegal", new String[]{"legal", "待法务"},
            "taskFinance", new String[]{"finance", "待财务"},
            "taskGm", new String[]{"gm", "待总经理"},
            "taskSeal", new String[]{"seal", "待用印"},
            "taskArchive", new String[]{"archive", "待归档"},
            "taskMail", new String[]{"mail", "待邮寄"}
    );

    @Resource
    private FinanceContractApplicationService contractApplicationService;

    @Override
    public void notify(DelegateTask delegateTask) {
        if (delegateTask == null || !EVENTNAME_CREATE.equals(delegateTask.getEventName())) {
            return;
        }
        Long appId = resolveAppId(delegateTask);
        if (appId == null) {
            log.warn("[notify] cannot resolve contract appId for task {}",
                    delegateTask.getTaskDefinitionKey());
            return;
        }
        String[] mapped = NODE_MAP.get(delegateTask.getTaskDefinitionKey());
        String key = mapped != null ? mapped[0] : delegateTask.getTaskDefinitionKey();
        String name = mapped != null ? mapped[1] : delegateTask.getName();
        contractApplicationService.updateCurrentNode(appId, key, name);
        log.info("[notify][contract appId({}) node={}/{}]", appId, key, name);
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
                // fall through
            }
        }
        return null;
    }
}
