package cn.iocoder.yudao.module.finance.dal.dataobject.allocation;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("finance_dept_cost_allocation")
@KeySequence("finance_dept_cost_allocation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDeptCostAllocationDO extends TenantBaseDO {

    public static final String SOURCE_SALARY = "薪资";
    public static final String SOURCE_CLOUD = "云服务";
    public static final String SOURCE_OTHER = "其他";

    @TableId
    private Long id;
    /** 期间 YYYY-MM */
    private String period;
    /** 来源类型：薪资 / 云服务 / 其他（表单选择，不在 Excel 列） */
    private String sourceType;
    private Long deptId;
    /** 导入时部门名称快照 */
    private String deptName;
    private BigDecimal amount;
    private String remark;
    private Long importerId;
    private LocalDateTime importTime;
}
