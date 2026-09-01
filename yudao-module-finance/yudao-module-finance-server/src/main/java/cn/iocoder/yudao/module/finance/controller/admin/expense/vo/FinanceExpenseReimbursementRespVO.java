package cn.iocoder.yudao.module.finance.controller.admin.expense.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class FinanceExpenseReimbursementRespVO {

    private Long id;
    private String applicationNo;
    private String processTitle;
    private String periodLabel;
    private String payeeAccountName;
    private String payeeBankName;
    private String payeeAccountNo;
    private BigDecimal applyAmount;
    private BigDecimal approvedAmount;
    private Boolean proxyTicket;
    private String status;
    private String processInstanceId;
    /** 审批流程是否已结束；无流程实例视为已结束 */
    private Boolean processEnded;
    private Long applicantUserId;
    private Long actualUserId;
    private Long applicantDeptId;
    private Long entityCompanyDeptId;
    private String entityCompanyName;
    private LocalDate applyDate;
    private String financeComment;
    private LocalDate actualPayDate;
    private Long companyBankAccountId;
    private String payVoucherUrl;
    private List<String> extraAttachments;
    private List<FinanceExpenseReimbursementLineReqVO> lines;
}
