package cn.iocoder.yudao.module.finance.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 合同签约 createAndStart Request VO")
@Data
public class FinanceContractApplicationCreateAndStartReqVO {

    @Schema(description = "对方客商公司编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "对方客商公司不能为空")
    private Long counterpartyCompanyId;

    @Schema(description = "金额不适用")
    private Boolean amountNa;

    @Schema(description = "合同金额")
    private BigDecimal contractAmount;

    @Schema(description = "交易币种 CNY/USD/HKD；金额不适用时可空，否则必填")
    private String currency;

    @Schema(description = "签约主体组织部门 ID（启用公司）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "签约主体不能为空")
    private Long entityCompanyDeptId;

    @Schema(description = "签约主体名称（兼容旧客户端；服务端忽略，以组织快照为准）")
    private String signCompany;

    @Schema(description = "用印文件名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用印文件名称不能为空")
    private String fileName;

    @Schema(description = "文件类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件类型不能为空")
    private String fileType;

    @Schema(description = "产品类型（EXP-70 必填，字典 finance_product_type）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "产品类型不能为空")
    private String productType;

    @Schema(description = "返点比例", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "返点比例不能为空")
    private String rebateRatio;

    @Schema(description = "结算方式", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "结算方式不能为空")
    private String settlementMethod;

    @Schema(description = "文件份数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件份数不能为空")
    private Integer copyCount;

    @Schema(description = "印章类型（JSON 或逗号分隔）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "印章类型不能为空")
    private String sealTypes;

    @Schema(description = "是否邮寄", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否邮寄不能为空")
    private Boolean needMail;

    @Schema(description = "邮寄地址")
    private String mailAddress;

    @Schema(description = "前置流程引用")
    private String preProcessRef;

    @Schema(description = "起始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "用印文件电子版 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用印文件电子版不能为空")
    private String draftFileUrl;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "业务人员用户编号，默认提单人")
    private Long businessStaffUserId;

    @Schema(description = "申请人部门编号")
    private Long applicantDeptId;

    @Schema(description = "发起人自选审批人")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "发起时选择的任职公司")
    private Long startCompanyDeptId;

    @Schema(description = "发起时选择的任职部门")
    private Long startDeptId;
}
