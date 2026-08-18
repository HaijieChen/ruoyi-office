package cn.iocoder.yudao.module.bpm.framework.security;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * OA 单据详情读权：本人或当前 process 上 active 任务候选人/办理人。
 * 抄送人不是读者。不信任客户端 taskId。
 */
@Component("oaBillAccess")
public class OaBillAccessPermission {

    @Resource
    private ObjectProvider<TaskService> taskServiceProvider;

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

}
