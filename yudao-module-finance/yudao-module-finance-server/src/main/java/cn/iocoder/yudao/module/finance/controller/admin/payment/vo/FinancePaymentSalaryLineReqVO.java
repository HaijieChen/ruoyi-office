package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "薪资付款明细 Request VO")
@Data
public class FinancePaymentSalaryLineReqVO {

    @NotNull(message = "主体公司不能为空")
    private Long entityCompanyDeptId;

    @NotNull(message = "公司银行账户不能为空")
    private Long companyBankAccountId;

    @NotNull(message = "实发薪资不能为空")
    private BigDecimal netSalaryAmount;

    @NotNull(message = "个税不能为空")
    private BigDecimal personalTaxAmount;

    @NotNull(message = "社保不能为空")
    private BigDecimal socialInsuranceAmount;

    @Schema(description = "公积金，可不填")
    private BigDecimal housingFundAmount;

}
