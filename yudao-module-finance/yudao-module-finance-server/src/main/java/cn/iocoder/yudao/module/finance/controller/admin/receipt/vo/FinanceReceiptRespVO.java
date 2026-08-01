package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 银行到款 Response VO")
@Data
public class FinanceReceiptRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "到款流水号")
    private String receiptNo;
    @Schema(description = "导入日期")
    private LocalDate importDate;
    @Schema(description = "导入人编号")
    private Long importerId;
    @Schema(description = "银行账户")
    private String bankAccount;
    @Schema(description = "主体公司组织部门编号")
    private Long entityCompanyDeptId;
    @Schema(description = "主体公司名称")
    private String entityCompanyName;
    @Schema(description = "交易日期")
    private LocalDateTime transactionDate;
    @Schema(description = "付款方名称")
    private String payerName;
    @Schema(description = "付款方账号")
    private String payerAccount;
    @Schema(description = "交易金额")
    private BigDecimal transactionAmount;
    @Schema(description = "摘要/附言")
    private String summary;
    @Schema(description = "银行流水号")
    private String bankSerialNo;
    @Schema(description = "认领状态")
    private Integer claimStatus;
    @Schema(description = "已认领金额")
    private BigDecimal claimedAmount;
    @Schema(description = "未认领金额")
    private BigDecimal unclaimedAmount;
    @Schema(description = "待确认认领占用金额")
    private BigDecimal pendingClaimedAmount;
    @Schema(description = "可认领金额（服务端：unclaimed − pending）")
    private BigDecimal claimableAmount;

}
