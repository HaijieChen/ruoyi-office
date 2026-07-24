package cn.iocoder.yudao.module.finance.dal.dataobject.claim;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

@TableName("finance_receipt_claim_item")
@KeySequence("finance_receipt_claim_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReceiptClaimItemDO extends BaseDO {

    @TableId
    private Long id;
    private Long claimId;
    private Long receiptId;
    private Long businessOrderId;
    private BigDecimal claimAmount;

}
