package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceShareDO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface BpmProcessInstanceShareService {

    void share(Long operatorUserId, String processInstanceId, Collection<Long> recipientUserIds);

    void revoke(Long operatorUserId, String processInstanceId, Long recipientUserId);

    List<BpmProcessInstanceShareDO> getActiveRecipients(Long operatorUserId, String processInstanceId);

    PageResult<BpmProcessInstanceShareDO> getSharedWithMePage(Long recipientUserId, PageParam pageParam);

    boolean hasActiveShare(Long recipientUserId, String processInstanceId);

    Set<String> listActiveSharedInstanceIds(Long recipientUserId);

    /** 身份：发起人或未收回分享。不含已通过判定。 */
    boolean canAccessRelated(Long userId, String processInstanceId);

    /** 详情：发起人 / 任务办理人 / 抄送 / 分享 / 流程管理员。 */
    boolean canViewDetail(Long userId, String processInstanceId);

    void assertCanViewDetail(Long userId, String processInstanceId);
}
