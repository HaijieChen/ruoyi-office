package cn.iocoder.yudao.module.finance.dal.dataobject.invoice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("finance_invoice_redflush")
@KeySequence("finance_invoice_redflush_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceInvoiceRedflushDO extends BaseDO {

    @TableId
    private Long id;
    private String applicationNo;
    private String processInstanceId;
    private Long predecessorApplicationId;
    private String approvalStatus;
    private Integer issueStatus;
    private String reason;
    private String specialNote;
    private BigDecimal totalAmount;
    private String currency;
    private Long applicantUserId;
    private Boolean voided;
}
