package cn.iocoder.yudao.module.bpm.framework.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.task.BpmFinanceAttachAccess;
import jakarta.annotation.Resource;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * OA 单据详情读权（与财务 {@code FinanceProcessParticipantSupport} 同一规则）：
 * 发起人、当前待办办理人/候选人、历史任务办理人一直可读。抄送人不是读者。
 */
@Component("oaBillAccess")
public class OaBillAccessPermission {

    @Resource
    private ObjectProvider<TaskService> taskServiceProvider;
    @Resource
    private ObjectProvider<HistoryService> historyServiceProvider;
    @Resource
    private ObjectProvider<BpmFinanceAttachAccess> financeAttachAccessProvider;

    public boolean canTaskContextOrOwnerRead(Long ownerUserId, String processInstanceId) {
        Long userId = getLoginUserId();
        if (userId == null) {
            return false;
        }
        if (Objects.equals(ownerUserId, userId)) {
            return true;
        }
        return isActiveTaskCandidateOrAssignee(processInstanceId, userId);
    }

    /**
     * 用户是否为该 process 上任意 active 任务的候选人或办理人。
     * 仅用于详情读权，不扩大列表。
     */
    public boolean isActiveTaskCandidateOrAssignee(String processInstanceId, Long userId) {
        if (userId == null || StrUtil.isBlank(processInstanceId)) {
            return false;
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            return false;
        }
        long count = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskCandidateOrAssigned(String.valueOf(userId))
                .count();
        return count > 0;
    }

    /**
     * 已办：流程结束后办理人仍可读详情。
     */
    public boolean isHistoricTaskAssignee(String processInstanceId, Long userId) {
        if (userId == null || StrUtil.isBlank(processInstanceId)) {
            return false;
        }
        if (historyServiceProvider == null) {
            return false;
        }
        HistoryService historyService = historyServiceProvider.getIfAvailable();
        if (historyService == null) {
            return false;
        }
        long count = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(String.valueOf(userId))
                .count();
        return count > 0;
    }

    public boolean canReadOaBill(Long userId, Long ownerUserId, String processInstanceId) {
        if (userId == null) {
            return false;
        }
        if (Objects.equals(userId, ownerUserId)) {
            return true;
        }
        return isActiveTaskCandidateOrAssignee(processInstanceId, userId)
                || isHistoricTaskAssignee(processInstanceId, userId)
                || canReadViaAttachingBill(userId, processInstanceId);
    }

    public boolean canReadViaAttachingBill(Long userId, String processInstanceId) {
        if (financeAttachAccessProvider == null) {
            return false;
        }
        BpmFinanceAttachAccess access = financeAttachAccessProvider.getIfAvailable();
        return access != null && access.canReadProcessInstanceViaBill(userId, processInstanceId);
    }

}
