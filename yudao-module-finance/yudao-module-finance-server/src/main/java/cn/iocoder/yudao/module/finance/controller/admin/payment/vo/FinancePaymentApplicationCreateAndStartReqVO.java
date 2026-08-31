package cn.iocoder.yudao.module.finance.controller.admin.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 付款申请 createAndStart Request VO")
@Data
public class FinancePaymentApplicationCreateAndStartReqVO {

    @Schema(description = "支付时效 IMMEDIATE/MONTH_END/ON_NOTICE")
    @NotEmpty(message = "支付时效不能为空")
    private String paymentTiming;

    @Schema(description = "付款事由 BUSINESS/PURCHASE/SALARY/TAX/LEASE/OTHER")
    @NotEmpty(message = "付款事由不能为空")
    private String paymentReason;

    @Schema(description = "采购流程实例 id（事由=PURCHASE 必填）")
    private String purchaseProcessInstanceId;

    @Schema(description = "租赁合同申请 id（事由=LEASE 必填）")
    private Long leaseContractApplicationId;

    @Schema(description = "关联合同申请 id（事由=BUSINESS 必填，须为付款业务合同）")
    private Long relatedContractApplicationId;

    @Schema(description = "收款方客商 id")
    @NotNull(message = "收款方不能为空")
    private Long payeeCompanyId;

    @Schema(description = "本次开户行快照（空则用档案）")
    private String payeeBankName;

    @Schema(description = "本次账号快照（空则用档案）")
    private String payeeBankAccount;

    @Schema(description = "主体公司组织部门 ID（启用公司）")
    @NotNull(message = "主体公司不能为空")
    private Long entityCompanyDeptId;

    @Schema(description = "申请金额")
    @NotNull(message = "申请金额不能为空")
    private BigDecimal applyAmount;

    @Schema(description = "交易币种 CNY/USD/HKD（切换主体后可改）")
    @NotEmpty(message = "币种不能为空")
    private String currency;

    @Schema(description = "业务结算账期（日期 YYYY-MM-DD）")
    @NotEmpty(message = "业务结算账期不能为空")
    private String businessSettlementTerm;

    @Schema(description = "支付方式（已停用，可空）")
    private String payMethod;

    @Schema(description = "费用归属项目")
    @NotEmpty(message = "费用归属项目不能为空")
    private String costProject;

    @Schema(description = "付款依据文件 URL 列表")
    @NotEmpty(message = "付款依据附件不能为空")
    private List<String> evidenceFileUrls;

    @Schema(description = "特殊说明")
    private String specialNote;

    @Schema(description = "业务人员用户编号，默认提单人")
    private Long businessStaffUserId;

    @Schema(description = "申请人部门")
    private Long applicantDeptId;

    @Schema(description = "自选审批人")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "发起时选择的任职公司")
    private Long startCompanyDeptId;

    @Schema(description = "发起时选择的任职部门")
    private Long startDeptId;

}
