package cn.iocoder.yudao.module.bpm.api.task;

import java.util.Collection;
import java.util.Set;

/**
 * 发起人公司下指定职务的审批人。由 HRM 实现。
 */
public interface BpmJobPositionApproverProvider {

    Set<Long> listUserIdsByJobPositions(Collection<String> jobPositions, Long companyDeptId);
}
