package cn.iocoder.yudao.module.finance.controller.admin.expense.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceExpenseApproveReqVO {

    @NotNull
    private Long id;
    @NotNull
    private BigDecimal approvedAmount;
    private String financeComment;
    private String taskId;
}
