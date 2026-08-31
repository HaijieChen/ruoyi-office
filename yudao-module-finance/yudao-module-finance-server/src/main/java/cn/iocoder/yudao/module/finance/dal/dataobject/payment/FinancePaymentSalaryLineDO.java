package cn.iocoder.yudao.module.finance.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("finance_payment_salary_line")
@KeySequence("finance_payment_salary_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancePaymentSalaryLineDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long paymentApplicationId;
    private Long entityCompanyDeptId;
    private String entityCompanyName;
    private Long companyBankAccountId;
    private String accountNameSnapshot;
    private String bankNameSnapshot;
    private String accountNoMaskedSnapshot;
    private BigDecimal netSalaryAmount;
    private BigDecimal personalTaxAmount;
    private BigDecimal socialInsuranceAmount;
    private BigDecimal housingFundAmount;
    private String currency;
    private BigDecimal lineTotal;
    private Integer sort;

}
