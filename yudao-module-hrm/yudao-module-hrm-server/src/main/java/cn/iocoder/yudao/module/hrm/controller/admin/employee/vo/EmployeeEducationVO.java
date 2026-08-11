package cn.iocoder.yudao.module.hrm.controller.admin.employee.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - 员工教育经历 VO")
@Data
public class EmployeeEducationVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED, example = "2015-09-01")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate startTime;

    @Schema(description = "截止时间", example = "2019-06-30")
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate endTime;

    @Schema(description = "学历", example = "6")
    private String educationLevel;

    @Schema(description = "学历类别", example = "1")
    private String educationType;

    @Schema(description = "学位", example = "学士")
    private String degree;

    @Schema(description = "是否第一学历", example = "true")
    private Boolean firstEducation;

    @Schema(description = "是否最高学历", example = "true")
    private Boolean highestEducation;

    @Schema(description = "专业", example = "计算机科学与技术")
    private String major;

    @Schema(description = "学校名称", example = "清华大学")
    private String schoolName;

}

