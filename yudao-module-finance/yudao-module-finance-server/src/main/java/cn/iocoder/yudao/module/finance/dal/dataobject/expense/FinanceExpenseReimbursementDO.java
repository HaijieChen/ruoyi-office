package cn.iocoder.yudao.module.finance.dal.dataobject.expense;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("finance_expense_reimbursement")
@KeySequence("finance_expense_reimbursement_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceExpenseReimbursementDO extends TenantBaseDO {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_WAIT_PAY = "WAIT_PAY";
    public static final String STATUS_PAID = "PAID";

    @TableId
    private Long id;
    private String processTitle;
    private String periodLabel;
    private String payeeAccountName;
    private String payeeAccountNo;
    private BigDecimal applyAmount;
    private BigDecimal approvedAmount;
    private Long companyBankAccountId;
    private Boolean proxyTicket;
    private String status;
    private String processInstanceId;
    private Long applicantUserId;
    private Long applicantDeptId;
    private LocalDate applyDate;
    private String financeComment;
    private LocalDate actualPayDate;
    private String payVoucherUrl;
}
