package cn.iocoder.yudao.module.finance.dal.dataobject.opening;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 公司银行账户期初余额。每账户仅一条有效记录，二次写入 upsert。
 */
@TableName("finance_bank_opening_balance")
@KeySequence("finance_bank_opening_balance_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceBankOpeningBalanceDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 公司银行账户 finance_company_bank_account.id */
    private Long accountId;
    /** 期初日期 */
    private LocalDate asOfDate;
    private BigDecimal amount;
    private String currency;

}
