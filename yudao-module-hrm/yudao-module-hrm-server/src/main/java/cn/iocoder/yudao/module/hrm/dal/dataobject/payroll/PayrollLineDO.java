package cn.iocoder.yudao.module.hrm.dal.dataobject.payroll;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("hrm_payroll_line")
@KeySequence("hrm_payroll_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PayrollLineDO extends BaseDO {

    @TableId
    private Long id;
    private Long batchId;
    private Long employeeId;
    private String employeeName;
    private String punchName;
    private BigDecimal payable;
    private BigDecimal net;
    private BigDecimal tax;
    private BigDecimal overtime;
    private String idCard;
    private String bankAccount;
    private Boolean snapshot;
    private Long userId;
    private BigDecimal sickDays;
}
