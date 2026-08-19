package cn.iocoder.yudao.module.finance.controller.admin.expense.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FinanceExpenseRecordPayReqVO {

    @NotNull
    private Long id;
    @NotNull
    private Long companyBankAccountId;
    @NotNull
    private LocalDate actualPayDate;
    @NotBlank
    private String payVoucherUrl;
    private String taskId;
}
