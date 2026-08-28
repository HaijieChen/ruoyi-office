package cn.iocoder.yudao.module.finance.controller.admin.expense.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FinanceExpenseReimbursementCreateReqVO {

    private Long actualUserId;
    @NotBlank
    private String periodLabel;
    @NotNull
    private Boolean proxyTicket;
    @NotBlank
    private String payeeAccountName;
    @NotBlank
    private String payeeBankName;
    @NotBlank
    private String payeeAccountNo;
    @NotEmpty
    @Valid
    private List<FinanceExpenseReimbursementLineReqVO> lines;

    private Long startCompanyDeptId;
    private Long startDeptId;
}
