package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceInvoiceApplicationExportExcelVO {

    @ExcelProperty("开票申请单号")
    private String applicationNo;
    @ExcelProperty("审批状态")
    private String approvalStatus;
    @ExcelProperty("购方名称")
    private String buyerName;
    @ExcelProperty("开票金额")
    private BigDecimal totalAmount;
    @ExcelProperty("币种")
    private String currency;
    @ExcelProperty("开票主体公司")
    private String invoiceCompany;
    @ExcelProperty("发票类型")
    private String invoiceType;
}
