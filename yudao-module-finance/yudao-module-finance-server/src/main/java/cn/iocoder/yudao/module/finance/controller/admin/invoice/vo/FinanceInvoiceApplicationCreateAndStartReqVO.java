package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 开票申请 createAndStart Request VO")
@Data
public class FinanceInvoiceApplicationCreateAndStartReqVO {

    @Schema(description = "期望开票日")
    private LocalDate expectedInvoiceDate;

    @Schema(description = "开票公司组织部门编号（orgType=公司）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开票公司不能为空")
    private Long invoiceCompanyDeptId;

    @Schema(description = "开票公司名称快照（服务端忽略，以组织快照为准）")
    private String invoiceCompany;

    @Schema(description = "开票币种 CNY/USD/HKD", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "币种不能为空")
    private String currency;

    @Schema(description = "发票类型：字典 finance_invoice_type 的 value（专票/普票）")
    private String invoiceType;

    @Schema(description = "客户公司编号（必须选启用档案；服务端写购方快照）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "必须选择客户公司")
    private Long customerCompanyId;

    @Schema(description = "购方名称（可选展示；服务端以档案覆盖）")
    private String buyerName;

    @Schema(description = "购方税号（可选展示；服务端以档案覆盖）")
    private String buyerTaxNo;

    @Schema(description = "购方地址电话（可选展示；服务端以档案覆盖）")
    private String buyerAddressPhone;

    @Schema(description = "购方银行账号（可选展示；服务端以档案覆盖）")
    private String buyerBankAccount;

    @Schema(description = "特殊开票要求")
    private String specialInvoiceRequirement;

    @Schema(description = "产品类型（字典 finance_product_type；品牌商务=ppsw）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请先选择产品类型")
    private String taxContent;

    @Schema(description = "税率（提交快照）")
    private BigDecimal taxRate;

    @Schema(description = "不含税金额")
    private BigDecimal amountExcludingTax;

    @Schema(description = "税额")
    private BigDecimal taxAmount;

    @Schema(description = "开票依据附件 URL")
    private String evidenceFileUrl;

    @Schema(description = "备注/特殊情况说明")
    private String remark;

    @Schema(description = "业务人员用户编号，默认提单人")
    private Long businessStaffUserId;

    @Schema(description = "发起人自选审批人")
    private Map<String, List<Long>> startUserSelectAssignees;

    @Schema(description = "发起时选择的任职公司")
    private Long startCompanyDeptId;

    @Schema(description = "发起时选择的任职部门")
    private Long startDeptId;

    @Schema(description = "开票明细", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "开票明细不能为空")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {

        @Schema(description = "商务单编号（品牌商务 ppsw 必填）")
        private Long businessOrderId;

        @Schema(description = "前置销售合同编号（非品牌商务必填）")
        private Long sourceContractApplicationId;

        @Schema(description = "本行开票金额", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "开票金额不能为空")
        @DecimalMin(value = "0.01", message = "开票金额必须大于 0")
        private BigDecimal amount;

        @Schema(description = "开票公司（行快照）")
        private String invoiceCompany;

        @Schema(description = "发票类型（行快照）")
        private String invoiceType;

        @Schema(description = "业务账期（日期 YYYY-MM-DD）")
        private String billingPeriod;

        @Schema(description = "明细备注")
        private String remark;

        @Schema(description = "行序")
        private Integer sort;

    }

}
