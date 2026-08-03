package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceReceiptImportExcelVO {

    @ExcelProperty("主体公司")
    private String entityCompanyName;

    @ExcelProperty("银行账户")
    private String bankAccount;

    /**
     * 交易日期原始值（字符串）。
     * <p>
     * Excel 数值日期与文本日期均经 {@link FinanceReceiptImportDateStringConverter}
     * 转为字符串，再由 {@code FinanceReceiptImportDateParser} 按档 1 规则解析，
     * 避免 FastExcel 对 {@code yyyy-MM-dd} 文本直接转 LocalDateTime 抛 500。
     */
    @ExcelProperty(value = "交易日期", converter = FinanceReceiptImportDateStringConverter.class)
    private String transactionDate;

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

    /**
     * 是否业务款原始值（字符串）。
     * 空/空白 → 否；支持 是/否、Y/N、true/false、1/0（大小写不敏感）。
     */
    @ExcelProperty("是否业务款")
    private String businessFund;

    @ExcelProperty("款项类型备注")
    private String fundTypeRemark;

}
