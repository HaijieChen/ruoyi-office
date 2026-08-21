package cn.iocoder.yudao.module.hrm.dal.dataobject.payroll;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("hrm_min_wage")
@KeySequence("hrm_min_wage_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class MinWageDO extends BaseDO {

    @TableId
    private Long id;

    private BigDecimal amount;

    /** YYYYMM */
    private Integer effectiveMonth;
}
