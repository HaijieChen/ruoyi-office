package cn.iocoder.yudao.module.finance.controller.admin.expense.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class FinanceExpenseReimbursementRespVO {

    private Long id;
    private String processTitle;
    private String periodLabel;
    private String payeeAccountName;
    private String payeeAccountNo;
    private BigDecimal applyAmount;
    private BigDecimal approvedAmount;
    private Boolean proxyTicket;
    private String status;
    private String processInstanceId;
    private Long applicantUserId;
    private Long applicantDeptId;
    private LocalDate applyDate;
    private String financeComment;
    private LocalDate actualPayDate;
    private Long companyBankAccountId;
    private List<FinanceExpenseReimbursementLineReqVO> lines;
}
