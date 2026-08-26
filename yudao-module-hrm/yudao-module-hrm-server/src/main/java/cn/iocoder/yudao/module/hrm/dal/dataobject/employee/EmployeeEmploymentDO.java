package cn.iocoder.yudao.module.hrm.dal.dataobject.employee;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 员工任职公司。主表 companyId 继续表示签约公司，供薪酬考勤使用。
 */
@TableName("hrm_employee_employment")
@KeySequence("hrm_employee_employment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeEmploymentDO extends BaseDO {

    @TableId
    private Long id;
    private Long employeeId;
    /** 任职公司 = system_dept.id（orgType=公司） */
    private Long companyDeptId;
    /** 是否签约公司 */
    private Boolean signed;
}
