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
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String MODE_WITH_INVOICE = "WITH_INVOICE";
    public static final String MODE_NO_INVOICE = "NO_INVOICE";

    @TableId
    private Long id;
    private String applicationNo;
    private String processTitle;
    private String periodLabel;
    private String payeeAccountName;
    private String payeeBankName;
    private String payeeAccountNo;
    private BigDecimal applyAmount;
    private BigDecimal approvedAmount;
    private Long companyBankAccountId;
    private Boolean proxyTicket;
    private String invoiceMode;
    private String processKey;
    private String status;
    private String processInstanceId;
    private Long applicantUserId;
    private Long actualUserId;
    private Long applicantDeptId;
    private Long entityCompanyDeptId;
    private String entityCompanyName;
    private LocalDate applyDate;
    private String financeComment;
    private LocalDate actualPayDate;
    private String payVoucherUrl;
    /** 其他附件 URL，逗号分隔，最多 30 个 */
    private String extraAttachments;
}
