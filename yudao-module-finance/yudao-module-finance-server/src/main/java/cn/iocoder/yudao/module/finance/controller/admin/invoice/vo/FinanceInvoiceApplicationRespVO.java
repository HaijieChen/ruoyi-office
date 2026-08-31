package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 开票申请 Response VO")
@Data
public class FinanceInvoiceApplicationRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "申请单号")
    private String applicationNo;
    @Schema(description = "流程实例编号")
    private String processInstanceId;
    @Schema(description = "审批状态")
    private String approvalStatus;
    @Schema(description = "办票状态")
    private Integer issueStatus;
    @Schema(description = "价税合计")
    private BigDecimal totalAmount;
    @Schema(description = "开票币种 CNY/USD/HKD")
    private String currency;
    @Schema(description = "已确认认领金额")
    private BigDecimal confirmedClaimedAmount;
    @Schema(description = "待确认认领金额")
    private BigDecimal pendingClaimedAmount;
    @Schema(description = "申请人用户编号")
    private Long applicantUserId;
    private Long businessStaffUserId;
    @Schema(description = "期望开票日")
    private LocalDate expectedInvoiceDate;
    @Schema(description = "开票公司名称快照")
    private String invoiceCompany;
    @Schema(description = "开票公司组织部门编号")
    private Long invoiceCompanyDeptId;
    @Schema(description = "发票类型（字典 finance_invoice_type）")
    private String invoiceType;
    @Schema(description = "购方名称（快照）")
    private String buyerName;
    @Schema(description = "购方税号（快照）")
    private String buyerTaxNo;
    @Schema(description = "购方地址电话（快照）")
    private String buyerAddressPhone;
    @Schema(description = "购方银行账号（快照）")
    private String buyerBankAccount;
    @Schema(description = "弱关联客户公司编号")
    private Long customerCompanyId;
    @Schema(description = "特殊开票要求")
    private String specialInvoiceRequirement;
    @Schema(description = "产品类型/开票内容")
    private String taxContent;
    @Schema(description = "税率")
    private BigDecimal taxRate;
    @Schema(description = "不含税金额")
    private BigDecimal amountExcludingTax;
    @Schema(description = "税额")
    private BigDecimal taxAmount;
    @Schema(description = "开票依据附件")
    private String evidenceFileUrl;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "是否作废")
    private Boolean voided;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "明细行")
    private List<Line> lines;

    @Schema(description = "办票附件（整单多附件）")
    private List<FileItem> files;

    @Schema(description = "可认领金额（服务端计算：total − confirmed − pending）")
    private BigDecimal claimableAmount;

    @Data
    public static class FileItem {
        @Schema(description = "附件编号")
        private Long id;
        @Schema(description = "申请编号")
        private Long applicationId;
        @Schema(description = "附件 URL")
        private String fileUrl;
        @Schema(description = "文件名")
        private String fileName;
        @Schema(description = "排序")
        private Integer sort;
    }

    @Data
    public static class Line {
        @Schema(description = "明细编号")
        private Long id;
        @Schema(description = "申请编号")
        private Long applicationId;
        @Schema(description = "商务单编号")
        private Long businessOrderId;
        @Schema(description = "商务单号（标识，非产品历史）")
        private String businessOrderNo;
        /** 提交时来源合同（仅行级快照，空表示历史未证实） */
        @Schema(description = "来源合同签约申请编号（行级历史快照，仅读库）")
        private Long sourceContractApplicationId;
        @Schema(description = "来源合同业务单号（由行级 source 解析，不回退当前 BO）")
        private String contractApplicationNo;
        /** 提交时产品（仅行级快照） */
        @Schema(description = "产品类型快照（行级历史，仅读库）")
        private String productType;
        @Schema(description = "历史产品未证实（行 snapshot 空）")
        private Boolean historyProductUnproven;
        @Schema(description = "历史来源合同未证实（行 source 空）")
        private Boolean historySourceContractUnproven;
        /** 以下为当前态展示，不得当作提交时事实 */
        @Schema(description = "当前商务单合同 id（现值，非历史）")
        private Long currentContractApplicationId;
        @Schema(description = "当前商务单合同业务单号（现值）")
        private String currentContractApplicationNo;
        @Schema(description = "当前商务单产品类型（现值，优先 snapshot）")
        private String currentProductType;
        @Schema(description = "本行金额")
        private BigDecimal amount;
        @Schema(description = "开票公司")
        private String invoiceCompany;
        @Schema(description = "发票类型")
        private String invoiceType;
        @Schema(description = "业务账期")
        private String billingPeriod;
        @Schema(description = "明细备注")
        private String remark;
        @Schema(description = "行办票状态")
        private Integer issueStatus;
        @Schema(description = "发票号")
        private String invoiceNo;
        @Schema(description = "发票附件")
        private String fileUrl;
        @Schema(description = "开票时间")
        private LocalDateTime issuedAt;
        @Schema(description = "行序")
        private Integer sort;
    }

}
