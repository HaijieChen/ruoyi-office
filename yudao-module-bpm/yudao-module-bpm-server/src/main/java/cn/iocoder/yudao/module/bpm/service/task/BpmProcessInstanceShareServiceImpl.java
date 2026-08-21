package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceShareDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceCopyMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceShareMapper;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_SHARE_NOT_APPROVED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_SHARE_NOT_INITIATOR;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_SHARE_SELF;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_SHARE_USER_INVALID;

@Service
@Validated
public class BpmProcessInstanceShareServiceImpl implements BpmProcessInstanceShareService {

    @Resource
    private BpmProcessInstanceShareMapper shareMapper;
    @Resource
    private BpmProcessInstanceCopyMapper copyMapper;
    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmTaskService taskService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Override
    public void share(Long operatorUserId, String processInstanceId, Collection<Long> recipientUserIds) {
        HistoricProcessInstance instance = requireApprovedOwned(operatorUserId, processInstanceId);
        Set<Long> recipients = new LinkedHashSet<>();
        for (Long id : recipientUserIds) {
            if (id != null) {
                recipients.add(id);
            }
        }
        if (recipients.isEmpty()) {
            throw exception(PROCESS_INSTANCE_SHARE_USER_INVALID);
        }
        if (recipients.contains(operatorUserId)) {
            throw exception(PROCESS_INSTANCE_SHARE_SELF);
        }
        for (Long recipientId : recipients) {
            AdminUserRespDTO user = adminUserApi.getUser(recipientId).getCheckedData();
            if (user == null || !Objects.equals(user.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
                throw exception(PROCESS_INSTANCE_SHARE_USER_INVALID);
            }
            BpmProcessInstanceShareDO existing = shareMapper.selectByInstanceAndRecipient(processInstanceId, recipientId);
            if (existing == null) {
                shareMapper.insert(BpmProcessInstanceShareDO.builder()
                        .processInstanceId(processInstanceId)
                        .startUserId(operatorUserId)
                        .recipientUserId(recipientId)
                        .processInstanceName(instance.getName())
                        .revokedAt(null)
                        .build());
            } else if (existing.getRevokedAt() != null) {
                existing.setRevokedAt(null);
                existing.setProcessInstanceName(instance.getName());
                shareMapper.updateById(existing);
            }
        }
    }

    @Override
    public void revoke(Long operatorUserId, String processInstanceId, Long recipientUserId) {
        HistoricProcessInstance instance = processInstanceService.getHistoricProcessInstance(processInstanceId);
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        if (!Objects.equals(String.valueOf(operatorUserId), instance.getStartUserId())) {
            throw exception(PROCESS_INSTANCE_SHARE_NOT_INITIATOR);
        }
        BpmProcessInstanceShareDO existing = shareMapper.selectByInstanceAndRecipient(processInstanceId, recipientUserId);
        if (existing == null || existing.getRevokedAt() != null) {
            return;
        }
        existing.setRevokedAt(LocalDateTime.now());
        shareMapper.updateById(existing);
    }

    @Override
    public List<BpmProcessInstanceShareDO> getActiveRecipients(Long operatorUserId, String processInstanceId) {
        HistoricProcessInstance instance = processInstanceService.getHistoricProcessInstance(processInstanceId);
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        if (!Objects.equals(String.valueOf(operatorUserId), instance.getStartUserId())) {
            throw exception(PROCESS_INSTANCE_SHARE_NOT_INITIATOR);
        }
        return shareMapper.selectActiveByInstance(processInstanceId);
    }

    @Override
    public PageResult<BpmProcessInstanceShareDO> getSharedWithMePage(Long recipientUserId, PageParam pageParam) {
        return shareMapper.selectActivePageByRecipient(recipientUserId, pageParam);
    }

    @Override
    public boolean hasActiveShare(Long recipientUserId, String processInstanceId) {
        BpmProcessInstanceShareDO row = shareMapper.selectByInstanceAndRecipient(processInstanceId, recipientUserId);
        return row != null && row.getRevokedAt() == null;
    }

    @Override
    public Set<String> listActiveSharedInstanceIds(Long recipientUserId) {
        List<BpmProcessInstanceShareDO> list = shareMapper.selectActiveByRecipient(recipientUserId);
        if (CollUtil.isEmpty(list)) {
            return Set.of();
        }
        return list.stream().map(BpmProcessInstanceShareDO::getProcessInstanceId).collect(Collectors.toSet());
    }

    @Override
    public boolean canAccessRelated(Long userId, String processInstanceId) {
        HistoricProcessInstance instance = processInstanceService.getHistoricProcessInstance(processInstanceId);
        if (instance == null || userId == null) {
            return false;
        }
        if (Objects.equals(String.valueOf(userId), instance.getStartUserId())) {
            return true;
        }
        return hasActiveShare(userId, processInstanceId);
    }

    @Override
    public boolean canViewDetail(Long userId, String processInstanceId) {
        if (userId == null || processInstanceId == null) {
            return false;
        }
        if (securityFrameworkService.hasPermission("bpm:process-instance:manager-query")) {
            return true;
        }
        HistoricProcessInstance instance = processInstanceService.getHistoricProcessInstance(processInstanceId);
        if (instance == null) {
            return false;
        }
        if (Objects.equals(String.valueOf(userId), instance.getStartUserId())) {
            return true;
        }
        if (hasActiveShare(userId, processInstanceId)) {
            return true;
        }
        if (copyMapper.existsByInstanceAndUser(processInstanceId, userId)) {
            return true;
        }
        List<HistoricTaskInstance> tasks = taskService.getTaskListByProcessInstanceId(processInstanceId, true);
        if (CollUtil.isEmpty(tasks)) {
            return false;
        }
        String uid = String.valueOf(userId);
        return tasks.stream().anyMatch(t -> uid.equals(t.getAssignee()));
    }

    @Override
    public void assertCanViewDetail(Long userId, String processInstanceId) {
        if (!canViewDetail(userId, processInstanceId)) {
            throw exception(PROCESS_INSTANCE_ACCESS_DENIED);
        }
    }

    private HistoricProcessInstance requireApprovedOwned(Long operatorUserId, String processInstanceId) {
        HistoricProcessInstance instance = processInstanceService.getHistoricProcessInstance(processInstanceId);
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        if (!Objects.equals(String.valueOf(operatorUserId), instance.getStartUserId())) {
            throw exception(PROCESS_INSTANCE_SHARE_NOT_INITIATOR);
        }
        Integer status = FlowableUtils.getProcessInstanceStatus(instance);
        if (!Objects.equals(BpmProcessInstanceStatusEnum.APPROVE.getStatus(), status)) {
            throw exception(PROCESS_INSTANCE_SHARE_NOT_APPROVED);
        }
        return instance;
    }
}
