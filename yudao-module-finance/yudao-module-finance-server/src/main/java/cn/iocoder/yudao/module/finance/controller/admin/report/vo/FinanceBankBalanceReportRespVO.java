package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "管理后台 - 银行余额表")
@Data
public class FinanceBankBalanceReportRespVO {

    private List<Row> list;
    private Long excludedFxCount;
    private Long unmatchedReceiptCount;

    @Data
    public static class Row {
        private Long accountId;
        private String accountName;
        private String accountNoMasked;
        private Long entityCompanyDeptId;
        private BigDecimal openingAmount;
        private LocalDate openingAsOfDate;
        private BigDecimal incomeAmount;
        private BigDecimal payExpenseAmount;
        private BigDecimal reimbursementExpenseAmount;
        private BigDecimal balanceAmount;
        private LocalDate asOf;
    }
}
