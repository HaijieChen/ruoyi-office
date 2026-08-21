package cn.iocoder.yudao.module.hrm.dal.dataobject.payroll;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("hrm_payroll_batch")
@KeySequence("hrm_payroll_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class PayrollBatchDO extends BaseDO {

    @TableId
    private Long id;
    private Integer yearMonth;
    private String status;
}
