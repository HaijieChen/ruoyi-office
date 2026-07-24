package cn.iocoder.yudao.module.finance.controller.admin.claim.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 到款认领撤销 Request VO")
@Data
public class FinanceReceiptClaimRevokeReqVO {

    @Schema(description = "认领单编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "认领单编号不能为空")
    private Long id;

    @Schema(description = "撤销原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "撤销原因不能为空")
    private String reason;

}
