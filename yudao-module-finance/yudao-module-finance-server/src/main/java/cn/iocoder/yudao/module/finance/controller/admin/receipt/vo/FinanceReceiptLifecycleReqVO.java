package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 银行到款关闭或重开请求")
@Data
public class FinanceReceiptLifecycleReqVO {

    @Schema(description = "银行到款编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "银行到款编号不能为空")
    private Long id;

    @Schema(description = "操作原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "操作原因不能为空")
    private String reason;
}
