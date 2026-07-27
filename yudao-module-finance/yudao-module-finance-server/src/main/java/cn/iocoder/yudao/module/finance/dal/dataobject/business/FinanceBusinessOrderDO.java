package cn.iocoder.yudao.module.finance.dal.dataobject.business;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    private LocalDate importDate;
    private Long importerId;
    private String contractProcessId;
    private LocalDate orderDate;
    private String productName;
    private String contactPerson;
    private LocalDate executionStartDate;
    private LocalDate executionEndDate;
    private String payerName;
    private BigDecimal signedExecutionAmount;
    private BigDecimal discountRate;
    private BigDecimal settlementAmount;
    private String bankAccount;
    private String remark;
    private BigDecimal confirmedClaimedAmount;
    private String sourceRowHash;

}
