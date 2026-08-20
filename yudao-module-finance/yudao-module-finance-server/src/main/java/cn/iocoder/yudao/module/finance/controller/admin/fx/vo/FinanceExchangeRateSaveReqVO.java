package cn.iocoder.yudao.module.finance.controller.admin.fx.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceExchangeRateSaveReqVO {
    private Long id;
    @NotBlank
    private String periodLabel;
    @NotBlank
    private String fromCurrency;
    @NotBlank
    private String toCurrency;
    @NotNull
    private BigDecimal rate;
}
