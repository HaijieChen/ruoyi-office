package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "员工任职公司")
@Data
public class EmployeeEmploymentVO {

    @Schema(description = "任职公司部门编号")
    @NotNull(message = "任职公司不能为空")
    private Long companyDeptId;

    @Schema(description = "任职公司名称")
    private String companyName;

    @Schema(description = "是否签约公司")
    private Boolean signed;
}
