package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinanceBusinessOrderImportExcelVO {

    @ExcelProperty("主体公司")
    private String entityCompanyName;

    @ExcelProperty("合同审批流程ID")
    private String contractProcessId;

    @ExcelProperty("合同申请业务单号")
    private String contractApplicationNo;

    @ExcelProperty("下单日期")
    private LocalDate orderDate;

    @ExcelProperty("产品名称")
    private String productName;

    @ExcelProperty("对接人")
    private String contactPerson;

    @ExcelProperty("执行开始日")
    private LocalDate executionStartDate;

    @ExcelProperty("执行截止日")
    private LocalDate executionEndDate;

    @ExcelProperty("付款方名称")
    private String payerName;

    @ExcelProperty("签单执行金额")
    private BigDecimal signedExecutionAmount;

    @ExcelProperty("折扣率")
    private BigDecimal discountRate;

    @ExcelProperty("摘要/附言")
    private String summary;

}
