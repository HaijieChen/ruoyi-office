package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "税金付款明细 Response VO")
@Data
public class FinancePaymentTaxLineRespVO {

    private Long id;
    private Long entityCompanyDeptId;
    private String entityCompanyName;
    private Long companyBankAccountId;
    private String accountNameSnapshot;
    private String bankNameSnapshot;
    private String accountNoMaskedSnapshot;
    private BigDecimal vatAmount;
    private BigDecimal surchargeAmount;
    private BigDecimal stampTaxAmount;
    private BigDecimal citAmount;
    private String currency;
    private BigDecimal lineTotal;
    private Integer sort;

}
