package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 出纳 recordPay Request VO")
@Data
public class FinancePaymentRecordPayReqVO {

    @NotNull(message = "申请 id 不能为空")
    private Long id;

    @Schema(description = "BPM 出纳任务 id；流程已结束后从列表支付可空")
    private String taskId;

    @Schema(description = "公司银行账户 id（必选）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "付款账户不能为空")
    private Long companyBankAccountId;

    @Schema(description = "本笔支付金额；缺省则按剩余未付金额整笔支付")
    private BigDecimal payAmount;

    @NotNull(message = "实际支付日期不能为空")
    private LocalDate actualPayDate;

    @NotEmpty(message = "支付凭证不能为空")
    private String payVoucherUrl;

    @Schema(description = "ERP 凭证号（选填）")
    private String erpVoucherNo;

    @Schema(description = "幂等键（必填；同一申请+键重复提交返回已有明细）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "幂等键不能为空")
    private String idempotencyKey;

    @Schema(description = "是否在合计等于申请金额时 complete 出纳任务；默认 true")
    private Boolean completeWhenFullyPaid;

    @Schema(description = "资料/发票是否完整；false 则付款后待补票")
    private Boolean materialsComplete;

}
