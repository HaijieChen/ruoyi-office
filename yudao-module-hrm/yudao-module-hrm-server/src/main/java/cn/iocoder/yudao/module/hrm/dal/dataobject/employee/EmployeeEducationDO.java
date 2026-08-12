package cn.iocoder.yudao.module.hrm.dal.dataobject.employee;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/**
 * 员工教育经历 DO
 *
 * @author 宇擎源码
 */
@TableName("hrm_employee_education")
@KeySequence("hrm_employee_education_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeEducationDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 员工ID
     */
    private Long employeeId;

    /**
     * 开始时间
     */
    private LocalDate startTime;

    /**
     * 截止时间
     */
    private LocalDate endTime;

    /**
     * 学历
     */
    private String educationLevel;

    /**
     * 学历类别
     */
    private String educationType;

    /**
     * 学位
     */
    private String degree;

    /**
     * 是否第一学历
     */
    private Boolean firstEducation;

    /**
     * 是否最高学历
     */
    private Boolean highestEducation;

    /**
     * 专业
     */
    private String major;

    /**
     * 学校名称
     */
    private String schoolName;

}

