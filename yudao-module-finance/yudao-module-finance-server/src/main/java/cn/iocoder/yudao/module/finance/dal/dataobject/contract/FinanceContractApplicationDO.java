package cn.iocoder.yudao.module.finance.dal.dataobject.contract;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 财务合同签约申请台账 DO
 *
 * <p>对方名称为提交快照。勿对全字段使用 FieldStrategy.ALWAYS。
 */
@TableName("finance_contract_application")
@KeySequence("finance_contract_application_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceContractApplicationDO extends BaseDO {

    @TableId
    private Long id;
    private String applicationNo;
    private String processInstanceId;
    /** PENDING / APPROVED / REJECTED / CANCELLED */
    private String approvalStatus;
    private String currentNodeKey;
    private String currentNodeName;
    private Long applicantUserId;
    private Long applicantDeptId;
    private Long counterpartyCompanyId;
    private String counterpartyName;
    private Boolean amountNa;
    private BigDecimal contractAmount;
    private String signCompany;
    private String fileName;
    private String fileType;
    private String productType;
    private String rebateRatio;
    private String settlementMethod;
    private Integer copyCount;
    private String sealTypes;
    private Boolean needMail;
    private String mailAddress;
    private String preProcessRef;
    private LocalDate startDate;
    private LocalDate endDate;
    private String draftFileUrl;
    private String sealFileUrl;
    private Long actualSealerUserId;
    private LocalDateTime archivedAt;
    private String mailTrackingNo;
    private String remark;
    private Boolean voided;

}
