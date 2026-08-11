package cn.iocoder.yudao.module.finance.dal.dataobject.invoice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 财务开票申请明细 DO（一期：一行一票）
 */
@TableName("finance_invoice_application_line")
@KeySequence("finance_invoice_application_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceInvoiceApplicationLineDO extends BaseDO {

    @TableId
    private Long id;
    private Long applicationId;
    private Long businessOrderId;
    /**
     * 来源合同签约申请 id（提交时从商务单复制）
     */
    private Long sourceContractApplicationId;
    /**
     * 产品类型快照（提交时从商务单复制）
     */
    private String productTypeSnapshot;
    private BigDecimal amount;
    private String invoiceCompany;
    private String invoiceType;
    /**
     * 业务账期 YYYY-MM
     */
    private String billingPeriod;
    /**
     * 行办票状态：0 未开 / 1 已开
     */
    private Integer issueStatus;
    private String invoiceNo;
    private String fileUrl;
    private LocalDateTime issuedAt;
    private Integer sort;

}
