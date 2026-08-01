package cn.iocoder.yudao.module.finance.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 合同签约审批落账 Request VO")
@Data
public class FinanceContractApplicationApprovalOutcomeReqVO {

    @NotNull
    private Long applicationId;

    @NotBlank
    @Schema(description = "APPROVED / REJECTED / CANCELLED")
    private String outcome;
}
