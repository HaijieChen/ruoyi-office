package cn.iocoder.yudao.module.hrm.controller.admin.payroll.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PayrollExportExcelVO {

    @ExcelProperty("年月")
    private Integer yearMonth;
    @ExcelProperty("公司")
    private String companyName;
    @ExcelProperty("部门")
    private String deptName;
    @ExcelProperty("岗位")
    private String jobPost;
    @ExcelProperty("姓名")
    private String name;
    @ExcelProperty("入职日期")
    private LocalDate entryDate;
    @ExcelProperty("工资")
    private BigDecimal wage;
    @ExcelProperty("社保基数")
    private BigDecimal socialBase;
    @ExcelProperty("公积金基数")
    private BigDecimal housingBase;
    @ExcelProperty("全勤奖")
    private BigDecimal fullAttendanceBonus;
    @ExcelProperty("住房补贴")
    private BigDecimal housingSubsidy;
    @ExcelProperty("绩效")
    private BigDecimal performance;
    @ExcelProperty("奖金")
    private BigDecimal bonus;
    @ExcelProperty("补贴")
    private BigDecimal subsidy;
    @ExcelProperty("法定节假日加班")
    private BigDecimal holidayOvertimeDays;
    @ExcelProperty("法定节假日加班费")
    private BigDecimal holidayOvertimePay;
    @ExcelProperty("工作日加班补贴")
    private BigDecimal weekdayOvertimePay;
    @ExcelProperty("病假天数")
    private BigDecimal sickDays;
    @ExcelProperty("病假系数")
    private BigDecimal sickRate;
    @ExcelProperty("病假扣除")
    private BigDecimal sickPay;
    @ExcelProperty("事假/缺勤天数")
    private BigDecimal personalAbsenceDays;
    @ExcelProperty("事假扣除")
    private BigDecimal personalLeavePay;
    @ExcelProperty("出差补贴")
    private BigDecimal tripSubsidy;
    @ExcelProperty("其它加减")
    private BigDecimal otherAdjust;
    @ExcelProperty("应付工资")
    private BigDecimal payable;
    @ExcelProperty("社保扣除")
    private BigDecimal socialDeduct;
    @ExcelProperty("公积金扣除")
    private BigDecimal housingDeduct;
    @ExcelProperty("个人所得税")
    private BigDecimal tax;
    @ExcelProperty("实发工资")
    private BigDecimal net;
    @ExcelProperty("银行卡号")
    private String bankAccount;
    @ExcelProperty("开户支行")
    private String bankName;
    @ExcelProperty("手机号码")
    private String mobile;
    @ExcelProperty("身份证号码")
    private String idCard;
}
