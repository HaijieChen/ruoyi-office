package cn.iocoder.yudao.module.finance.controller.admin.expense.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class FinanceExpenseReimbursementLineReqVO {

    @NotBlank
    private String lineKind;
    @NotBlank
    private String category;
    @NotNull
    private LocalDate feeDate;
    @NotNull
    private BigDecimal amount;
    private List<String> attachments;
    private String invoiceFileUrl;
    private String invoiceNo;
    private String predocType;
    private String predocProcessInstanceId;
    private String remark;
    private String stayCityTier;
    private String overLimitReason;
}
