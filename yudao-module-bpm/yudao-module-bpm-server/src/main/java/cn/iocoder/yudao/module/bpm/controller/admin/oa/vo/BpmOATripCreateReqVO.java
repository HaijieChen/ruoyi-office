package cn.iocoder.yudao.module.bpm.controller.admin.oa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 出差申请创建 Request VO")
@Data
public class BpmOATripCreateReqVO {

    @Schema(description = "出差类型（历史，新单可不填）", example = "1")
    private Integer type;

    @Schema(description = "出差地点", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "出差地点不能为空")
    private String destination;

    @Schema(description = "出差原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "出差原因不能为空")
    private String reason;

    @Schema(description = "同行人员用户编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "同行人员不能为空")
    private Long companionUserId;

    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime startTime;

    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime endTime;

    @Schema(description = "发起人自选审批人 Map（服务端忽略）", example = "{taskKey1: [1, 2]}")
    private Map<String, List<Long>> startUserSelectAssignees;

    @AssertTrue(message = "结束时间，需要在开始时间之后")
    public boolean isEndTimeValid() {
        return getStartTime() == null || getEndTime() == null || getEndTime().isAfter(getStartTime());
    }

}
