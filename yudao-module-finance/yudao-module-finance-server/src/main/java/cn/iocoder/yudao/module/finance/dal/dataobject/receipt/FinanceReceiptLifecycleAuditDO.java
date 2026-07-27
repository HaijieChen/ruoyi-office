package cn.iocoder.yudao.module.finance.dal.dataobject.receipt;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("finance_receipt_lifecycle_audit")
@KeySequence("finance_receipt_lifecycle_audit_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReceiptLifecycleAuditDO extends BaseDO {

    @TableId
    private Long id;
    private Long receiptId;
    private Integer action;
    private Long operatorId;
    private LocalDateTime actionTime;
    private String reason;
}
