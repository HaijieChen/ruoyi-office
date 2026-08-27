package cn.iocoder.yudao.module.hrm.framework.bpm;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.api.task.BpmJobPositionApproverProvider;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeDO;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.EmployeeEmploymentDO;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeEmploymentMapper;
import cn.iocoder.yudao.module.hrm.dal.mysql.employee.EmployeeMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class HrmJobPositionApproverProviderImpl implements BpmJobPositionApproverProvider {

    private final EmployeeMapper employeeMapper;
    private final EmployeeEmploymentMapper employmentMapper;

    public HrmJobPositionApproverProviderImpl(EmployeeMapper employeeMapper,
                                              EmployeeEmploymentMapper employmentMapper) {
        this.employeeMapper = employeeMapper;
        this.employmentMapper = employmentMapper;
    }

    @Override
    public Set<Long> listUserIdsByJobPositions(Collection<String> jobPositions, Long companyDeptId) {
        if (CollUtil.isEmpty(jobPositions) || companyDeptId == null) {
            return Set.of();
        }
        List<EmployeeDO> employees = employeeMapper.selectListByJobPositions(jobPositions);
        if (CollUtil.isEmpty(employees)) {
            return Set.of();
        }
        Set<Long> employedIds = new HashSet<>();
        List<EmployeeEmploymentDO> employments =
                employmentMapper.selectListByCompanyDeptIds(List.of(companyDeptId));
        if (CollUtil.isNotEmpty(employments)) {
            for (EmployeeEmploymentDO row : employments) {
                if (row.getEmployeeId() != null) {
                    employedIds.add(row.getEmployeeId());
                }
            }
        }
        Set<Long> userIds = new HashSet<>();
        for (EmployeeDO employee : employees) {
            if (employee.getUserId() == null) {
                continue;
            }
            boolean inCompany = companyDeptId.equals(employee.getCompanyId())
                    || employedIds.contains(employee.getId());
            if (inCompany) {
                userIds.add(employee.getUserId());
            }
        }
        return userIds;
    }
}
