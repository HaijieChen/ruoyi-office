package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商务签单 Response VO")
@Data
public class FinanceBusinessOrderRespVO {

    @Schema(description = "编号")
    private Long id;
    @Schema(description = "商务签单号")
    private String orderNo;
    @Schema(description = "导入日期")
    private LocalDate importDate;
    @Schema(description = "导入人编号")
    private Long importerId;
    @Schema(description = "导入人名称")
    private String importerName;
    @Schema(description = "合同审批流程编号")
    private String contractProcessId;
    @Schema(description = "下单日期")
    private LocalDate orderDate;
    @Schema(description = "产品名称")
    private String productName;
    @Schema(description = "对接人")
    private String contactPerson;
    @Schema(description = "执行开始日")
    private LocalDate executionStartDate;
    @Schema(description = "执行截止日")
    private LocalDate executionEndDate;
    @Schema(description = "付款方名称")
    private String payerName;
    @Schema(description = "签单执行金额")
    private BigDecimal signedExecutionAmount;
    @Schema(description = "折扣率")
    private BigDecimal discountRate;
    @Schema(description = "签单结算金额")
    private BigDecimal settlementAmount;
    @Schema(description = "银行账户")
    private String bankAccount;
    @Schema(description = "备注")
    private String remark;
    @Schema(description = "已确认认领金额")
    private BigDecimal confirmedClaimedAmount;
    @Schema(description = "剩余可认领余额 = 结算金额 - 已确认认领金额")
    private BigDecimal remainingBalance;
    @Schema(description = "来源行哈希")
    private String sourceRowHash;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
