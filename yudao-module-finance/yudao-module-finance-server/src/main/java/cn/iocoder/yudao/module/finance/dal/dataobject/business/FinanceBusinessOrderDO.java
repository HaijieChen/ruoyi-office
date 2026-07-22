package cn.iocoder.yudao.module.finance.dal.dataobject.business;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("finance_business_order")
@KeySequence("finance_business_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceBusinessOrderDO extends BaseDO {

    @TableId
    private Long id;
    private String orderNo;
    private String businessSubject;
    private String businessType;
    private String contractRef;
    private String projectRef;
    private BigDecimal receivableAmount;
    private BigDecimal payableAmount;
    private String currency;
    private Long ownerId;
    private Integer status;
    private String remark;

}
