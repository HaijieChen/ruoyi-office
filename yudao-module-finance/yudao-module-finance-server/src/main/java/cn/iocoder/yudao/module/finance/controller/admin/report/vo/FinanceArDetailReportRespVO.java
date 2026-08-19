package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 应收明细行 Response VO")
@Data
public class FinanceArDetailReportRespVO {

    @Schema(description = "商务单编号")
    private Long id;
    @Schema(description = "商务单号")
    private String orderNo;
    @Schema(description = "主体公司组织部门编号")
    private Long entityCompanyDeptId;
    @Schema(description = "主体公司")
    private String entityCompanyName;
    @Schema(description = "产品类型")
    private String productType;
    @Schema(description = "签单结算金额")
    private BigDecimal settlementAmount;
    @Schema(description = "已开票金额（占用）")
    private BigDecimal invoicedOccupiedAmount;
    @Schema(description = "已认款金额")
    private BigDecimal confirmedClaimedAmount;
    @Schema(description = "未开票应收")
    private BigDecimal uninvoicedAmount;
    @Schema(description = "已开票应收")
    private BigDecimal invoicedArAmount;
    @Schema(description = "应收合计")
    private BigDecimal arTotalAmount;
    @Schema(description = "币种（入表行恒为 CNY）")
    private String currency;
}
