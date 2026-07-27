package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceReceiptImportExcelVO {

    @ExcelProperty("银行账户")
    private String bankAccount;

    @ExcelProperty("交易日期")
    private LocalDateTime transactionDate;

    @ExcelProperty("付款方名称")
    private String payerName;

    @ExcelProperty("付款方账号")
    private String payerAccount;

    @ExcelProperty("交易金额")
    private BigDecimal transactionAmount;

    @ExcelProperty("摘要/附言")
    private String summary;

    @ExcelProperty("银行流水号")
    private String bankSerialNo;

}
