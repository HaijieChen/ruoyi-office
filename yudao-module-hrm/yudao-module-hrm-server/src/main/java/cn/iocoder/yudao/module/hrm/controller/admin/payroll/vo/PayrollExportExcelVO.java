package cn.iocoder.yudao.module.hrm.controller.admin.payroll.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayrollExportExcelVO {

    @ExcelProperty("姓名")
    private String name;
    @ExcelProperty("应付工资")
    private BigDecimal payable;
    @ExcelProperty("加班")
    private BigDecimal overtime;
    @ExcelProperty("个人所得税")
    private BigDecimal tax;
    @ExcelProperty("实发工资")
    private BigDecimal net;
}
