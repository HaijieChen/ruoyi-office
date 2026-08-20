package cn.iocoder.yudao.module.finance.dal.dataobject.fx;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("finance_exchange_rate")
@KeySequence("finance_exchange_rate_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceExchangeRateDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String periodLabel;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal rate;
}
