package cn.iocoder.yudao.module.finance.dal.dataobject.claim;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("finance_receipt_claim_revoke_audit")
@KeySequence("finance_receipt_claim_revoke_audit_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReceiptClaimRevokeAuditDO extends BaseDO {

    @TableId
    private Long id;
    private Long claimId;
    private Long reviewerId;
    private LocalDateTime revokeTime;
    private String revokeReason;

}
