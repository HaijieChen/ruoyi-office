package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "薪资付款明细 Response VO")
@Data
public class FinancePaymentSalaryLineRespVO {

    private Long id;
    private Long entityCompanyDeptId;
    private String entityCompanyName;
    private Long companyBankAccountId;
    private String accountNameSnapshot;
    private String bankNameSnapshot;
    private String accountNoMaskedSnapshot;
    private BigDecimal netSalaryAmount;
    private BigDecimal personalTaxAmount;
    private BigDecimal socialInsuranceAmount;
    private String currency;
    private BigDecimal lineTotal;
    private Integer sort;

}
