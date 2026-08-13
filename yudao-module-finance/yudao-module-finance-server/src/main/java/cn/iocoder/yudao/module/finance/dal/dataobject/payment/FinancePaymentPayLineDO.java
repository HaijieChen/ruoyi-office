package cn.iocoder.yudao.module.finance.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 付款支付明细：按实际转账笔次落库，账户信息为支付时不可变快照。
 */
@TableName("finance_payment_pay_line")
@KeySequence("finance_payment_pay_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancePaymentPayLineDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long paymentApplicationId;
    private Long companyBankAccountId;
    private Long entityCompanyDeptId;
    private String accountNameSnapshot;
    private String bankNameSnapshot;
    private String accountHolderSnapshot;
    private String accountNoSnapshot;
    private String accountNoMaskedSnapshot;
    private String currencySnapshot;
    private BigDecimal payAmount;
    private LocalDate actualPayDate;
    private String payVoucherUrl;
    private String erpVoucherNo;
    private String idempotencyKey;

}
