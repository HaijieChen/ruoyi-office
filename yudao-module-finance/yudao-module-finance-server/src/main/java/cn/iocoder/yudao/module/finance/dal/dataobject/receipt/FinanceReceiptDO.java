package cn.iocoder.yudao.module.finance.dal.dataobject.receipt;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("finance_bank_receipt")
@KeySequence("finance_bank_receipt_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReceiptDO extends BaseDO {

    @TableId
    private Long id;
    private String receiptNo;
    private LocalDate importDate;
    private Long importerId;
    private String bankAccount;
    private LocalDateTime transactionDate;
    private String payerName;
    private String payerAccount;
    private BigDecimal transactionAmount;
    private String summary;
    private String bankSerialNo;
    private Integer claimStatus;
    private BigDecimal claimedAmount;
    private BigDecimal unclaimedAmount;

}
