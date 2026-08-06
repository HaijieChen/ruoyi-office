package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 付款申请 Response VO")
@Data
public class FinancePaymentApplicationRespVO {

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
    private LocalDateTime createTime;
    /** 同收款方已支付累计（不含本次审批中） */
    private BigDecimal cumulativePaid;
    /** 本次支付后累计 = cumulativePaid + applyAmount */
    private BigDecimal cumulativeAfter;

}
