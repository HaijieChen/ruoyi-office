package cn.iocoder.yudao.module.bpm.controller.admin.oa.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 补卡申请 Response VO")
@Data
public class BpmOAPunchCorrectionRespVO {

    @Schema(description = "补卡表单主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "申请人的用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "申请人昵称", example = "张三")
    private String userNickname;

    @Schema(description = "申请人部门", example = "研发部")
    private String deptName;

    @Schema(description = "补卡日期", requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-08-15")
    private LocalDate punchDate;

    @Schema(description = "补卡时间（日期+时刻）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime punchTime;

    @Schema(description = "补卡事由", requiredMode = Schema.RequiredMode.REQUIRED, example = "忘记打卡")
    private String reason;

    @Schema(description = "申请时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "说明附件 URL 数组")
    private List<String> attachmentUrls;

    @Schema(description = "流程编号")
    private String processInstanceId;

    @Schema(description = "审批结果", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "考勤同步状态", example = "NOT_SYNCED")
    private String attendanceSyncStatus;

}
