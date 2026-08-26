package cn.iocoder.yudao.module.hrm.api.employee.dto;

import lombok.Data;

@Data
public class EmployeeColleagueRespDTO {

    private Long userId;
    private Long employeeId;
    private String name;
}
