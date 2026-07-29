package cn.iocoder.yudao.module.finance.controller.admin.claim.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 到款认领新增/修改 Request VO（新链路挂开票申请）")
@Data
public class FinanceReceiptClaimSaveReqVO {

    @Schema(description = "认领单编号，修改时必填")
    private Long id;

    @Schema(description = "说明")
    private String remark;

    @Schema(description = "认领分摊明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "认领明细不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @Schema(description = "银行到款编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "银行到款编号不能为空")
        private Long receiptId;

        @Schema(description = "开票申请编号（新链路必填）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "开票申请编号不能为空")
        private Long invoiceApplicationId;

        @Schema(description = "认领金额", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "认领金额不能为空")
        @DecimalMin(value = "0.01", message = "认领金额必须大于 0")
        private BigDecimal claimAmount;

    }

}
