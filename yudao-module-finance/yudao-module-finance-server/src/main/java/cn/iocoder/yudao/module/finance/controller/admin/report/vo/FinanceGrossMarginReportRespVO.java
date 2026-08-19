package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 产品毛利表行")
@Data
public class FinanceGrossMarginReportRespVO {

    @ExcelProperty("月份")
    private String yearMonth;
    @ExcelProperty("部门ID")
    private Long deptId;
    @ExcelProperty("部门")
    private String deptName;
    @ExcelProperty("产品类型")
    private String productType;
    @ExcelProperty("收入")
    private BigDecimal incomeAmount;
    @ExcelProperty("支出")
    private BigDecimal costAmount;
    @ExcelProperty("毛利")
    private BigDecimal marginAmount;
}
