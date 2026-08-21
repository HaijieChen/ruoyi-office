package cn.iocoder.yudao.module.hrm.controller.admin.payroll.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class PunchTemplateExcelVO {

    @ExcelProperty("工号")
    private String employeeNo;
    @ExcelProperty("姓名")
    private String name;
    @ExcelProperty("日期")
    private String date;
    @ExcelProperty("迟到时间")
    private String late;
    @ExcelProperty("是否旷工")
    private String absence;
}
