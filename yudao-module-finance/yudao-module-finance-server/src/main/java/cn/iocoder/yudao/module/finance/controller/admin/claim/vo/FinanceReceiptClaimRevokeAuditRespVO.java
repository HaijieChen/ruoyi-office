package cn.iocoder.yudao.module.finance.controller.admin.claim.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 到款认领撤销审计 Response VO")
@Data
public class FinanceReceiptClaimRevokeAuditRespVO {

    @Schema(description = "审计编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "认领单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long claimId;

    @Schema(description = "撤销操作人编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long reviewerId;

    @Schema(description = "撤销操作人姓名", example = "系统管理员")
    private String reviewerName;

    /**
     * 前端展示兼容字段：与 reviewerId 相同
     */
    @Schema(description = "操作人编号（兼容前端 operatorId）", example = "2048")
    private Long operatorId;

    /**
     * 前端展示兼容字段：与 reviewerName 相同
     */
    @Schema(description = "操作人姓名（兼容前端 operatorName）", example = "系统管理员")
    private String operatorName;

    @Schema(description = "撤销时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime revokeTime;

    @Schema(description = "撤销原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "金额有误")
    private String revokeReason;

    /**
     * 前端展示兼容字段：与 revokeReason 相同
     */
    @Schema(description = "原因（兼容前端 reason）", example = "金额有误")
    private String reason;

    /**
     * 前端展示兼容字段：与 revokeTime 相同
     */
    @Schema(description = "时间（兼容前端 createTime）")
    private LocalDateTime createTime;

    @Schema(description = "操作类型展示", example = "撤销")
    private String action;

}
