package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "管理后台 - 出纳 recordPay Request VO")
@Data
public class FinancePaymentRecordPayReqVO {

    @NotNull(message = "申请 id 不能为空")
    private Long id;

    @NotEmpty(message = "任务 id 不能为空")
    private String taskId;

    @NotNull(message = "实际支付日期不能为空")
    private LocalDate actualPayDate;

    @NotEmpty(message = "支付凭证不能为空")
    private String payVoucherUrl;

    @Schema(description = "ERP 凭证号（选填）")
    private String erpVoucherNo;

}
