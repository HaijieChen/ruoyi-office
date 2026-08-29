package cn.iocoder.yudao.module.finance.framework.security;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 财务单据详情只读：本人、当前待办、或历史办理人。不要求必须停在审批节点。
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
        if (taskService != null
                && taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskCandidateOrAssigned(uid)
                .count() > 0) {
            return true;
        }
        HistoryService historyService = historyServiceProvider.getIfAvailable();
        if (historyService == null) {
            return false;
        }
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(uid)
                .count() > 0;
    }
}
