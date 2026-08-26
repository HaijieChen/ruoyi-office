package cn.iocoder.yudao.module.finance.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
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
public class FinanceContractApplicationDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String applicationNo;
    private String processInstanceId;
    /** PENDING / APPROVED / REJECTED / CANCELLED */
    private String approvalStatus;
    private String currentNodeKey;
    private String currentNodeName;
    private Long applicantUserId;
    private Long businessStaffUserId;
    private Long applicantDeptId;
    private Long counterpartyCompanyId;
    private String counterpartyName;
    private Boolean amountNa;
    private BigDecimal contractAmount;
    /** 交易币种 CNY/USD/HKD（历史可空） */
    private String currency;
    /**
     * 签约主体名称（历史字段；新单与 {@link #entityCompanyName} 同步写入以兼容旧读路径）
     */
    private String signCompany;
    /** 签约主体组织部门编号（启用公司） */
    private Long entityCompanyDeptId;
    /** 签约主体名称快照（仅服务端生成） */
    private String entityCompanyName;
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
