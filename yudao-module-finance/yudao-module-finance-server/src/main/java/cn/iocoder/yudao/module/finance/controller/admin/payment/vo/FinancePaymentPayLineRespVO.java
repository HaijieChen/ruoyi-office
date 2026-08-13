package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "付款支付明细 Response VO")
@Data
public class FinancePaymentPayLineRespVO {

    private Long id;
    private Long paymentApplicationId;
    private Long companyBankAccountId;
    private Long entityCompanyDeptId;
    private String accountNameSnapshot;
    private String bankNameSnapshot;
    private String accountHolderSnapshot;
    /** 脱敏账号 */
    private String accountNoMaskedSnapshot;
    private String currencySnapshot;
    private BigDecimal payAmount;
    private LocalDate actualPayDate;
    private String payVoucherUrl;
    private String erpVoucherNo;
    private LocalDateTime createTime;

}
