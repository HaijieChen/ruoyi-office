package cn.iocoder.yudao.module.finance.dal.dataobject.expense;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("finance_expense_reimbursement_line")
@KeySequence("finance_expense_reimbursement_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceExpenseReimbursementLineDO extends TenantBaseDO {

    public static final String KIND_NORMAL = "NORMAL";
    public static final String KIND_PROXY = "PROXY";

    @TableId
    private Long id;
    private Long reimbursementId;
    private String lineKind;
    private String category;
    private String invoiceType;
    private String subItem;
    private LocalDate feeDate;
    private BigDecimal amount;
    /** 专票税额（可空，OCR 回填后可改） */
    private BigDecimal taxAmount;
    private String attachments;
    private String invoiceFileUrl;
    private String invoiceNo;
    private String predocType;
    private String predocProcessInstanceId;
    private String remark;
    /** T1=北上广深 400；OTHER=其他 300 */
    private String stayCityTier;
    private String overLimitReason;
    private Integer sort;

    public static final String STAY_TIER_T1 = "T1";
    public static final String STAY_TIER_OTHER = "OTHER";
    public static final java.math.BigDecimal STAY_CAP_T1 = new java.math.BigDecimal("400");
    public static final java.math.BigDecimal STAY_CAP_OTHER = new java.math.BigDecimal("300");
}
