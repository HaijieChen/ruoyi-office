package cn.iocoder.yudao.module.hrm.dal.dataobject.payroll;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("hrm_payroll_line")
@KeySequence("hrm_payroll_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PayrollLineDO extends BaseDO {

    @TableId
    private Long id;
    private Long batchId;
    private Long employeeId;
    @TableField("`year_month`")
    private Integer yearMonth;
    private String companyName;
    private String deptName;
    private String jobPost;
    private String employeeName;
    private LocalDate entryDate;
    private BigDecimal wage;
    private BigDecimal socialBase;
    private BigDecimal housingBase;
    private BigDecimal fullAttendanceBonus;
    private BigDecimal housingSubsidy;
    private BigDecimal performance;
    private BigDecimal bonus;
    private BigDecimal subsidy;
    private BigDecimal holidayOvertimeDays;
    private BigDecimal holidayOvertimePay;
    private BigDecimal weekdayOvertimePay;
    private BigDecimal sickDays;
    private BigDecimal sickRate;
    private BigDecimal sickPay;
    private BigDecimal personalAbsenceDays;
    private BigDecimal personalLeavePay;
    private BigDecimal tripSubsidy;
    private BigDecimal otherAdjust;
    private BigDecimal payable;
    private BigDecimal socialDeduct;
    private BigDecimal housingDeduct;
    private BigDecimal tax;
    private BigDecimal overtime;
    private BigDecimal net;
    private String bankAccount;
    private String bankName;
    private String mobile;
    private String idCard;
    private String punchName;
    private Boolean snapshot;
    private Long userId;
}
