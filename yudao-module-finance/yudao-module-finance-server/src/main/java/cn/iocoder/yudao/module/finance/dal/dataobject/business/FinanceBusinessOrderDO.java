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
    /** 主体公司（我方签约主体）组织部门编号 */
    private Long entityCompanyDeptId;
    /** 主体公司名称快照 */
    private String entityCompanyName;
    private LocalDate importDate;
    private Long importerId;
    /** 提单人（数据权限按此人） */
    private Long applicantUserId;
    /** 提单人部门 */
    private Long applicantDeptId;
    private Long businessStaffUserId;
    private String contractProcessId;
    /**
     * 正式关联合同签约申请 id（CS-T1/T5）；legacy 文本见 {@link #contractProcessId}
     */
    private Long contractApplicationId;
    private LocalDate orderDate;
    /**
     * 产品名称（legacy 自由文本；新写由服务端用合同产品覆盖）
     */
    private String productName;
    /**
     * 产品类型快照（权威冗余，来自合同 product_type）
     */
    private String productTypeSnapshot;
    private String contactPerson;
    private LocalDate executionStartDate;
    private LocalDate executionEndDate;
    private String payerName;
    private BigDecimal signedExecutionAmount;
    private BigDecimal discountRate;
    private BigDecimal settlementAmount;
    /** 交易币种 CNY/USD/HKD（历史可空） */
    private String currency;
    private String remark;
    private BigDecimal confirmedClaimedAmount;
    /**
     * 开票占用金额（提交即占，见 phase2a）
     */
    private BigDecimal invoicedOccupiedAmount;
    private String sourceRowHash;

}
