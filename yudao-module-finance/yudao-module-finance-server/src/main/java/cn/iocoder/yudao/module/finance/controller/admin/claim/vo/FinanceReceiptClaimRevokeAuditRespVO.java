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

    @Schema(description = "撤销时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime revokeTime;

    @Schema(description = "撤销原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "金额有误")
    private String revokeReason;

}
