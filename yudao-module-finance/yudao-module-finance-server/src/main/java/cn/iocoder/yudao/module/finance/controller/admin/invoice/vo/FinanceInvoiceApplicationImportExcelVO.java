package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

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
public class FinanceInvoiceApplicationImportExcelVO {

    @ExcelProperty("开票申请单号")
    private String applicationNo;

    @ExcelProperty("申请人账号")
    private String applicantUsername;

    @ExcelProperty("购方名称")
    private String buyerName;

    @ExcelProperty("开票金额")
    private BigDecimal totalAmount;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("开票主体公司")
    private String invoiceCompany;

    @ExcelProperty("产品类型")
    private String productType;

    @ExcelProperty("开票时间")
    private String issueTime;

    @ExcelProperty("商务单号")
    private String businessOrderNo;

    @ExcelProperty("发票号")
    private String invoiceNo;
}
