package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 出纳 recordPay Request VO")
@Data
public class FinancePaymentRecordPayReqVO {

    @NotNull(message = "申请 id 不能为空")
    private Long id;

    @Schema(description = "BPM 出纳任务 id；流程已结束后从列表支付可空")
    private String taskId;

    @Schema(description = "公司银行账户 id；单行提交时必填")
    private Long companyBankAccountId;

    @Schema(description = "本笔支付金额；缺省则按剩余未付金额整笔支付")
    private BigDecimal payAmount;

    @Schema(description = "实际支付日期；单行提交时必填")
    private LocalDate actualPayDate;

    @Schema(description = "支付凭证；单行提交时必填")
    private String payVoucherUrl;

    @Schema(description = "ERP 凭证号（选填）")
    private String erpVoucherNo;

    @Schema(description = "幂等键；单行提交时必填")
    private String idempotencyKey;

    @Schema(description = "是否在合计等于申请金额时 complete 出纳任务；默认 true")
    private Boolean completeWhenFullyPaid;

    @Schema(description = "资料/发票是否完整；false 则付款后待补票")
    private Boolean materialsComplete;

    @Schema(description = "一次提交的多行回执；有值时按行落账，合计超额整单拒绝")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull(message = "付款账户不能为空")
        private Long companyBankAccountId;
        @NotNull(message = "本笔金额不能为空")
        private BigDecimal payAmount;
        @NotNull(message = "实际支付日期不能为空")
        private LocalDate actualPayDate;
        @NotEmpty(message = "支付凭证不能为空")
        private String payVoucherUrl;
        private String erpVoucherNo;
        @NotEmpty(message = "幂等键不能为空")
        private String idempotencyKey;
    }

}
