package cn.iocoder.yudao.module.finance.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 合同签约申请 Response VO")
@Data
public class FinanceContractApplicationRespVO {

    private Long id;
    private String applicationNo;
    private String processInstanceId;
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
    private LocalDateTime createTime;
}
