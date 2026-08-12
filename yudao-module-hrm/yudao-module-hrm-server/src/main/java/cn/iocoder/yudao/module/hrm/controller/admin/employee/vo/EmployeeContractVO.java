package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 员工合同明细 VO")
@Data
public class EmployeeContractVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "合同序号 1-4", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "合同序号不能为空")
    private Integer sequenceNo;

    @Schema(description = "合同类型", example = "1")
    private String contractType;

    @Schema(description = "合同开始日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2024-01-01")
    @NotNull(message = "合同开始日期不能为空")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate startDate;

    @Schema(description = "合同结束日期", example = "2027-01-01")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate endDate;

}
