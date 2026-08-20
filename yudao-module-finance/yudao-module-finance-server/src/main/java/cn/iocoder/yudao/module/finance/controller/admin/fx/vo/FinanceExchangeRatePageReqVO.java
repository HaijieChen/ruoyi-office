package cn.iocoder.yudao.module.finance.controller.admin.fx.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceExchangeRatePageReqVO extends PageParam {
    private String periodLabel;
    private String fromCurrency;
    private String toCurrency;
}
