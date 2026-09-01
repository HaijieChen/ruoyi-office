package cn.iocoder.yudao.module.finance.controller.admin.expense.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class FinanceExpenseRecordPayReqVO {

    @NotNull
    private Long id;
    @NotNull
    private Long companyBankAccountId;
    @NotNull
    private LocalDate actualPayDate;
    /** 支付附件，可选多张 */
    private List<String> payVoucherUrls;
    private String payVoucherUrl;
    private String taskId;
}
