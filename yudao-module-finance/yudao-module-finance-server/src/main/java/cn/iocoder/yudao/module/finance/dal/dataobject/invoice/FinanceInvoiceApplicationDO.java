package cn.iocoder.yudao.module.finance.dal.dataobject.invoice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务开票申请台账 DO
 *
 * <p>购方/税项等为提交时快照，不随客户档案回写历史。
 * <p>buyerAddressPhone / buyerBankAccount / specialInvoiceRequirement 等可空快照字段
 * ALWAYS 更新，保证 resubmit 时档案空段能落库覆盖旧值（review H1）。
 */
@TableName("finance_invoice_application")
@KeySequence("finance_invoice_application_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceInvoiceApplicationDO extends BaseDO {

    @TableId
    private Long id;
    private String applicationNo;
    private String processInstanceId;
    /**
     * 审批状态：PENDING / APPROVED / REJECTED / CANCELLED
     */
    private String approvalStatus;
    /**
     * 办票状态：0 未开票 / 1 部分 / 2 全部
     */
    private Integer issueStatus;
    private BigDecimal totalAmount;
    private BigDecimal confirmedClaimedAmount;
    private BigDecimal pendingClaimedAmount;
    private Long applicantUserId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expectedInvoiceDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String invoiceCompany;
    /**
     * 开票公司对应组织部门编号（orgType=公司）；名称快照见 {@link #invoiceCompany}
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long invoiceCompanyDeptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String invoiceType;
    private String buyerName;
    private String buyerTaxNo;
    /** 可空合成快照：档案无地址/电话时须写 null 覆盖 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String buyerAddressPhone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String buyerBankAccount;
    /**
     * 弱关联客户公司；票面以 buyer_* 快照为准；历史可空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerCompanyId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String specialInvoiceRequirement;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String taxContent;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal taxRate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal amountExcludingTax;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal taxAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String evidenceFileUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private Boolean voided;

}
