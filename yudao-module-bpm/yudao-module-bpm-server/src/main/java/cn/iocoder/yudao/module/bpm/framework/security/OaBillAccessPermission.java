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
     * 用户是否为该 process 上任意 active 任务的候选人、办理人或 task owner（委托前原办理人）。
     * 不是业务单据 creator。仅用于详情读权，不扩大列表。
     */
    public boolean isActiveTaskCandidateOrAssignee(String processInstanceId, Long userId) {
        if (userId == null || StrUtil.isBlank(processInstanceId)) {
            return false;
        }
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService == null) {
            return false;
        }
        String uid = String.valueOf(userId);
        long count = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskCandidateOrAssigned(uid)
                .count();
        if (count > 0) {
            return true;
        }
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskOwner(uid)
                .count() > 0;
    }

    /**
     * 已办：流程结束后 historic assignee 或 historic task owner 仍可读详情。
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
        String uid = String.valueOf(userId);
        long assignee = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(uid)
                .count();
        if (assignee > 0) {
            return true;
        }
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskOwner(uid)
                .count() > 0;
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
