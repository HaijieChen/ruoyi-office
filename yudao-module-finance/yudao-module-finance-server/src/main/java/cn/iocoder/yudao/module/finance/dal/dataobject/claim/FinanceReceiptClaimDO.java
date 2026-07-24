package cn.iocoder.yudao.module.finance.dal.dataobject.claim;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("finance_receipt_claim")
@KeySequence("finance_receipt_claim_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReceiptClaimDO extends BaseDO {

    @TableId
    private Long id;
    private Long claimantId;
    private Integer status;
    private BigDecimal totalClaimAmount;
    private String remark;
    private String rejectReason;
    private String revokeReason;
    private Long reviewerId;
    private LocalDateTime reviewTime;

}
