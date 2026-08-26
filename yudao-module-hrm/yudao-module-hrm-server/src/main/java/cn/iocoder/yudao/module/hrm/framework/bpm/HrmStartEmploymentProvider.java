package cn.iocoder.yudao.module.hrm.framework.bpm;

import cn.iocoder.yudao.module.bpm.api.task.BpmStartEmploymentProvider;
import cn.iocoder.yudao.module.hrm.controller.admin.employee.vo.EmployeeEmploymentVO;
import cn.iocoder.yudao.module.hrm.service.employee.EmployeeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HrmStartEmploymentProvider implements BpmStartEmploymentProvider {

    private final EmployeeService employeeService;

    public HrmStartEmploymentProvider(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Override
    public List<Employment> listByUserId(Long userId) {
        List<EmployeeEmploymentVO> rows = employeeService.listMyEmployments(userId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new Employment(
                        row.getDeptId(),
                        row.getCompanyDeptId(),
                        Boolean.TRUE.equals(row.getSigned()),
                        row.getCompanyName(),
                        row.getDeptName()))
                .toList();
    }
}
