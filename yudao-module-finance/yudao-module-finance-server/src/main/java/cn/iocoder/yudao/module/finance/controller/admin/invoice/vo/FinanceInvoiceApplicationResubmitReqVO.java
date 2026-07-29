package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 开票申请驳回后 resubmit Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceInvoiceApplicationResubmitReqVO extends FinanceInvoiceApplicationCreateAndStartReqVO {

    @Schema(description = "开票申请编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开票申请编号不能为空")
    private Long id;

    /**
     * 明细字段复用父类 {@link FinanceInvoiceApplicationCreateAndStartReqVO#getLines()}，校验见父类 @Valid。
     */
    @SuppressWarnings("unused")
    private void keepValidLines() {
        // 占位：继承 create VO 的 lines 与快照字段
    }

}
