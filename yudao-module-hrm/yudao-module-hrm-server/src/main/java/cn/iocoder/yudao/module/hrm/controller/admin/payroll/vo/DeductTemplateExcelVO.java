package cn.iocoder.yudao.module.hrm.controller.admin.payroll.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DeductTemplateExcelVO {

    @ExcelProperty("工号")
    private String employeeNo;
    @ExcelProperty("姓名")
    private String name;
    @ExcelProperty("个人所得税")
    private BigDecimal tax;
    @ExcelProperty("社保扣除")
    private BigDecimal socialDeduct;
    @ExcelProperty("公积金扣除")
    private BigDecimal housingDeduct;
}
