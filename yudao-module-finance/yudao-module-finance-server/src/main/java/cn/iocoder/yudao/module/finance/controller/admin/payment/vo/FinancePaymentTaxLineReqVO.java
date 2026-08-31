package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "税金付款明细 Request VO")
@Data
public class FinancePaymentTaxLineReqVO {

    @NotNull(message = "主体公司不能为空")
    private Long entityCompanyDeptId;

    @NotNull(message = "公司银行账户不能为空")
    private Long companyBankAccountId;

    private BigDecimal vatAmount;
    private BigDecimal surchargeAmount;
    private BigDecimal stampTaxAmount;
    private BigDecimal citAmount;

}
