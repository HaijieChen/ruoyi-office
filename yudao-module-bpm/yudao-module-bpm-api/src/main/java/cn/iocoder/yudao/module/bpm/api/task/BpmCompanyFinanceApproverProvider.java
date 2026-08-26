package cn.iocoder.yudao.module.bpm.api.task;

import java.util.Set;

/**
 * 主体公司财务审批人。由财务模块实现，BPM 选人策略调用。
 */
public interface BpmCompanyFinanceApproverProvider {

    Set<Long> listUserIdsByCompanyDeptId(Long companyDeptId);
}
