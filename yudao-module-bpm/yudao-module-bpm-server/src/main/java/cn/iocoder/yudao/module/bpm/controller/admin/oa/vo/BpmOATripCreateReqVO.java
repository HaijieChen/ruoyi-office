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

    @Schema(description = "历史市内/省内类型，新单不填")
    private Integer type;

    @Schema(description = "业务类型：1洽谈 2活动 3其他", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "出差类型不能为空")
    private Integer bizType;

    @Schema(description = "目的地城市", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "目的地城市不能为空")
    private String destination;

    @Schema(description = "出发城市", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "出发城市不能为空")
    private String originCity;

    @Schema(description = "出差事由", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "出差事由不能为空")
    private String reason;

    @Schema(description = "交通工具")
    private String transport;

    @Schema(description = "机酒预定情况")
    private String hotelBooking;

    @Schema(description = "公司全称或活动邀请方")
    private String partyName;

    @Schema(description = "具体地址")
    private String address;

    @Schema(description = "对接人姓名职务联系方式")
    private String contactInfo;

    @Schema(description = "是否需要内容产出")
    private String needOutput;

    @Schema(description = "是否有车马费")
    private String hasCarriageFee;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "附件 URL")
    private List<String> attachmentUrls;

    @Schema(description = "同行人员用户编号（兼容旧单选）")
    private Long companionUserId;

    @Schema(description = "同行人员用户编号列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private java.util.List<Long> companionUserIds;

    @Schema(description = "开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime startTime;

    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间不能为空")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime endTime;

    @Schema(description = "发起人自选审批人 Map（服务端忽略）")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "发起时选择的任职公司")
    private Long startCompanyDeptId;

    @AssertTrue(message = "结束时间，需要在开始时间之后")
    public boolean isEndTimeValid() {
        return getStartTime() == null || getEndTime() == null || getEndTime().isAfter(getStartTime());
    }

}
