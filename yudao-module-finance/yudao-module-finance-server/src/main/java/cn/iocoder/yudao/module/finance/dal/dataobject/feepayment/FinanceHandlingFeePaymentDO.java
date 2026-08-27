package cn.iocoder.yudao.module.finance.dal.dataobject.feepayment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 手续费付款台账。
 */
@TableName("finance_handling_fee_payment")
@KeySequence("finance_handling_fee_payment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceHandlingFeePaymentDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 付款日期 */
    private LocalDate feeDate;
    /** 金额 */
    private BigDecimal amount;
    /** 币种 CNY/USD/HKD */
    private String currency;
    /** 主体公司 = system_dept.id（orgType=公司） */
    private Long entityCompanyDeptId;
    /** 主体公司名称快照 */
    private String entityCompanyName;
    /** 公司银行账户编号 */
    private Long companyBankAccountId;
    /** 户名快照 */
    private String accountName;
    /** 开户行快照 */
    private String bankName;
    /** 银行账号快照 */
    private String accountNo;
    /** 账号掩码 */
    private String accountNoMasked;

}
