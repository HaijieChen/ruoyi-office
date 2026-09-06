package cn.iocoder.yudao.module.bpm.controller.admin.oa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;
import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 补卡申请创建 Request VO")
@Data
public class BpmOAPunchCorrectionCreateReqVO {

    @Schema(description = "补卡日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-08-15")
    @NotNull(message = "补卡日期不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate punchDate;

    @Schema(description = "补卡时间（日期+时刻）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "补卡时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime punchTime;

    @Schema(description = "补卡事由", requiredMode = Schema.RequiredMode.REQUIRED, example = "忘记打卡")
    @NotBlank(message = "补卡事由不能为空")
    private String reason;

    @Schema(description = "说明附件 URL 数组")
    private List<String> attachmentUrls;

    @Schema(description = "发起人自选审批人 Map（创建时忽略）", example = "{taskKey1: [1, 2]}")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "发起时选择的任职公司")
    private Long startCompanyDeptId;

}
