package cn.iocoder.yudao.module.bpm.controller.admin.oa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 加班申请创建 Request VO")
@Data
public class BpmOAOvertimeCreateReqVO {

    @Schema(description = "加班事由", requiredMode = Schema.RequiredMode.REQUIRED, example = "项目上线")
    @NotBlank(message = "加班事由不能为空")
    private String reason;

    @Schema(description = "加班的开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime startTime;

    @Schema(description = "加班的结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime endTime;

    @Schema(description = "是否法定节假日（bpm_oa_overtime_holiday）", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    @NotBlank(message = "是否法定节假日不能为空")
    private String holiday;

    @Schema(description = "附件 URL 数组")
    private List<String> attachmentUrls;

    @Schema(description = "发起人自选审批人 Map（创建时忽略）", example = "{taskKey1: [1, 2]}")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "发起时选择的任职公司")
    private Long startCompanyDeptId;

}
