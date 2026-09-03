package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinanceBusinessOrderExportExcelVO {

    @ExcelProperty("订单编号")
    private String orderNo;
    @ExcelProperty("导入日期")
    private LocalDate importDate;
    @ExcelProperty("提单人")
    private String applicantName;
    @ExcelProperty("主体公司")
    private String entityCompanyName;
    @ExcelProperty("合同业务单号")
    private String contractApplicationNo;
    @ExcelProperty("签单日期")
    private LocalDate orderDate;
    @ExcelProperty("产品类型")
    private String productType;
    @ExcelProperty("付款方")
    private String payerName;
    @ExcelProperty("签约执行金额")
    private BigDecimal signedExecutionAmount;
    @ExcelProperty("已确认认领金额")
    private BigDecimal confirmedClaimedAmount;
}
