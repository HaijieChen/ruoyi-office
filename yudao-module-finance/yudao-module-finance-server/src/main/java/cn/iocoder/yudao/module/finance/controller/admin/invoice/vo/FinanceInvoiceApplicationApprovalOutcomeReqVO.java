package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 开票申请 onApprovalOutcome Request VO（内部/联调）")
@Data
public class FinanceInvoiceApplicationApprovalOutcomeReqVO {

    @Schema(description = "开票申请编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开票申请编号不能为空")
    private Long applicationId;

    @Schema(description = "审批结果：APPROVED / REJECTED / CANCELLED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "审批结果不能为空")
    private String outcome;

}
