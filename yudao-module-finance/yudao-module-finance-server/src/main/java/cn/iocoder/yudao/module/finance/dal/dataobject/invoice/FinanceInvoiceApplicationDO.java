package cn.iocoder.yudao.module.finance.dal.dataobject.invoice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务开票申请台账 DO
 *
 * <p>购方/税项等为提交时快照，不随客户档案回写历史。
 * <p>注意：不要在本 DO 上使用 FieldStrategy.ALWAYS —— createAndStart 随后会
 * {@code updateById} 仅写 processInstanceId，ALWAYS 会把已插入的快照列刷成 null。
 * resubmit 清空可选快照请用 LambdaUpdateWrapper 显式 set。
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
    /** 开票币种 CNY/USD/HKD（历史可空） */
    private String currency;
    private BigDecimal confirmedClaimedAmount;
    private BigDecimal pendingClaimedAmount;
    private Long applicantUserId;
    private LocalDate expectedInvoiceDate;
    private String invoiceCompany;
    /**
     * 开票公司对应组织部门编号（orgType=公司）；名称快照见 {@link #invoiceCompany}
     */
    private Long invoiceCompanyDeptId;
    private String invoiceType;
    private String buyerName;
    private String buyerTaxNo;
    private String buyerAddressPhone;
    private String buyerBankAccount;
    /**
     * 弱关联客户公司；票面以 buyer_* 快照为准；历史可空
     */
    private Long customerCompanyId;
    private String specialInvoiceRequirement;
    private String taxContent;
    private BigDecimal taxRate;
    private BigDecimal amountExcludingTax;
    private BigDecimal taxAmount;
    private String evidenceFileUrl;
    private String remark;
    private Boolean voided;

}
