package cn.iocoder.yudao.module.finance.dal.dataobject.invoice;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 开票申请办票附件 DO（整单多附件，I2）
 */
@TableName("finance_invoice_application_file")
@KeySequence("finance_invoice_application_file_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceInvoiceApplicationFileDO extends BaseDO {

    @TableId
    private Long id;
    private Long applicationId;
    private String fileUrl;
    private String fileName;
    private BigDecimal amount;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private Integer sort;

}
