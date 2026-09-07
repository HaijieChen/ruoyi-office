package cn.iocoder.yudao.module.finance.framework.security;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 流程单据详情只读通用规则：发起人、当前待办办理人/候选人、历史任务办理人一直可读。
 * 不要求必须停在审批节点。OA 请假/出差/外出走同等规则（OaBillAccessPermission）。
 */
@Component
public class FinanceProcessParticipantSupport {

    @Resource
    private ObjectProvider<TaskService> taskServiceProvider;
    @Resource
    private ObjectProvider<HistoryService> historyServiceProvider;

    public boolean canReadBill(Long userId, Long applicantUserId, String processInstanceId) {
        if (userId == null) {
            return false;
        }
        if (Objects.equals(userId, applicantUserId)) {
            return true;
        }
        if (StrUtil.isBlank(processInstanceId)) {
            return false;
        }
        String uid = String.valueOf(userId);
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService != null) {
            if (taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .taskCandidateOrAssigned(uid)
                    .count() > 0) {
                return true;
            }
            if (taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .taskOwner(uid)
                    .count() > 0) {
                return true;
            }
        }
        HistoryService historyService = historyServiceProvider.getIfAvailable();
        if (historyService == null) {
            return false;
        }
        if (historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(uid)
                .count() > 0) {
            return true;
        }
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskOwner(uid)
                .count() > 0;
    }
}
