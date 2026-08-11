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
    /** 主体公司组织部门编号 */
    private Long entityCompanyDeptId;
    /** 主体公司名称快照 */
    private String entityCompanyName;
    private LocalDateTime transactionDate;
    private String payerName;
    private String payerAccount;
    private BigDecimal transactionAmount;
    /** 到款币种 CNY/USD/HKD（历史可空） */
    private String currency;
    private String summary;
    private String bankSerialNo;
    /** 是否业务款（仅展示，不联动认领） */
    private Boolean businessFund;
    /** 款项类型备注（选填） */
    private String fundTypeRemark;
    private Integer claimStatus;
    private BigDecimal claimedAmount;
    /**
     * 待确认认领占用金额（双边 pending，见 phase2a）
     */
    private BigDecimal pendingClaimedAmount;
    private BigDecimal unclaimedAmount;

}
