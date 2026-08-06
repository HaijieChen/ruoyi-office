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
    private String currentNodeKey;
    private String currentNodeName;
    private String processTitle;
    private Long applicantUserId;
    private Long applicantDeptId;
    private LocalDate applyDate;
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
