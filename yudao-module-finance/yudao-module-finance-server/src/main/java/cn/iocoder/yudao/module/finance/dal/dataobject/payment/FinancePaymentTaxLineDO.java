package cn.iocoder.yudao.module.finance.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("finance_payment_tax_line")
@KeySequence("finance_payment_tax_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancePaymentTaxLineDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long paymentApplicationId;
    private Long entityCompanyDeptId;
    private String entityCompanyName;
    private BigDecimal vatAmount;
    private BigDecimal surchargeAmount;
    private BigDecimal stampTaxAmount;
    private BigDecimal citAmount;
    private String currency;
    private BigDecimal lineTotal;
    private Integer sort;

}
