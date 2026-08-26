package cn.iocoder.yudao.module.bpm.api.task;

import java.util.List;

/**
 * 发起人任职公司/部门。由 HRM 实现，BPM 发起权与选人使用。
 */
public interface BpmStartEmploymentProvider {

    List<Employment> listByUserId(Long userId);

    record Employment(Long deptId, Long companyDeptId, boolean signed, String companyName, String deptName) {
    }
}
