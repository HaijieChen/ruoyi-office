package cn.iocoder.yudao.module.finance.controller.admin.fx.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FinanceExchangeRateRespVO {
    private Long id;
    private String periodLabel;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal rate;
    private LocalDateTime createTime;
}
