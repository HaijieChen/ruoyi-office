package cn.iocoder.yudao.module.hrm.dal.dataobject.employee;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * 员工合同明细 DO
 */
@TableName("hrm_employee_contract")
@KeySequence("hrm_employee_contract_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeContractDO extends BaseDO {

    @TableId
    private Long id;

    /**
     * 员工ID
     */
    private Long employeeId;

    /**
     * 合同序号 1-4
     */
    private Integer sequenceNo;

    /**
     * 合同类型
     */
    private String contractType;

    /**
     * 合同开始日期
     */
    private LocalDate startDate;

    /**
     * 合同结束日期
     */
    private LocalDate endDate;

}
