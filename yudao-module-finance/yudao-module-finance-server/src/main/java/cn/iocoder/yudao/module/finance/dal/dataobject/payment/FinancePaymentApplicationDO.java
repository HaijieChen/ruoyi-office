package cn.iocoder.yudao.module.finance.dal.dataobject.payment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("finance_payment_application")
@KeySequence("finance_payment_application_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancePaymentApplicationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String applicationNo;
    private String processInstanceId;
    private String status;
    /**
     * 业务类型：ORDINARY / SALARY / TAX。
     * 默认 ORDINARY 兼容历史单。
     */
    private String applicationKind;
    private String currentNodeKey;
    private String currentNodeName;
    private String processTitle;
    private Long applicantUserId;
    private Long applicantDeptId;
    /** 主体公司组织部门 ID（业务主体，非仅任职部门；薪资/税金主单可空，明细行承载） */
    private Long entityCompanyDeptId;
    /** 主体公司名称快照（仅服务端生成） */
    private String entityCompanyName;
    private LocalDate applyDate;
    /** 薪资期间 / 税款所属期 */
    private String periodLabel;
    private String paymentTiming;
    private String paymentReason;
    private String purchaseProcessInstanceId;
    private String purchaseSnapshot;
    private Long leaseContractApplicationId;
    private Long relatedContractApplicationId;
    private Long payeeCompanyId;
    private String payeeName;
    private String payeeBankName;
    private String payeeBankAccount;
    private BigDecimal applyAmount;
    private String currency;
    private String amountInWords;
    private String contractSettlementMethod;
    private String businessSettlementTerm;
    private String payMethod;
    private String costProject;
    private String accountingSubject;
    private String evidenceFileUrls;
    private String specialNote;
    private LocalDate actualPayDate;
    private String payVoucherUrl;
    private String erpVoucherNo;
    private Boolean voided;

}
