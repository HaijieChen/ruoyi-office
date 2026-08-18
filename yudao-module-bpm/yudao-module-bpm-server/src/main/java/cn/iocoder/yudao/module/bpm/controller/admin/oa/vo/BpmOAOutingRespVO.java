package cn.iocoder.yudao.module.bpm.controller.admin.oa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 外出申请 Response VO")
@Data
public class BpmOAOutingRespVO {

    @Schema(description = "外出表单主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "申请人的用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "申请人昵称", example = "张三")
    private String userNickname;

    @Schema(description = "申请人部门", example = "研发部")
    private String deptName;

    @Schema(description = "外出事由", requiredMode = Schema.RequiredMode.REQUIRED, example = "客户拜访")
    private String reason;

    @Schema(description = "外出地点", requiredMode = Schema.RequiredMode.REQUIRED, example = "上海")
    private String location;

    @Schema(description = "申请时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "外出的开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @Schema(description = "外出的结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;

    @Schema(description = "时长（小时）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1.5")
    private BigDecimal hours;

    @Schema(description = "是否需产出（infra_boolean_string）", example = "true")
    private String needOutput;

    @Schema(description = "附件 URL 数组")
    private List<String> attachmentUrls;

    @Schema(description = "流程编号")
    private String processInstanceId;

    @Schema(description = "审批结果", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "考勤同步状态", example = "NOT_SYNCED")
    private String attendanceSyncStatus;

}
