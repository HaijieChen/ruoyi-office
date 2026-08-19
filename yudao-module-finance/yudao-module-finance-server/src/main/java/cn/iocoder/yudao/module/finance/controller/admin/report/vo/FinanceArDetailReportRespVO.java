package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 应收明细行 Response VO")
@Data
public class FinanceArDetailReportRespVO {

    @ExcelIgnore
    @Schema(description = "商务单编号")
    private Long id;
    @ExcelProperty("商务单号")
    @Schema(description = "商务单号")
    private String orderNo;
    @ExcelIgnore
    @Schema(description = "主体公司组织部门编号")
    private Long entityCompanyDeptId;
    @ExcelProperty("主体公司")
    @Schema(description = "主体公司")
    private String entityCompanyName;
    @ExcelProperty("产品类型")
    @Schema(description = "产品类型")
    private String productType;
    @ExcelProperty("结算金额")
    @Schema(description = "签单结算金额")
    private BigDecimal settlementAmount;
    @ExcelProperty("已开票")
    @Schema(description = "已开票金额（占用）")
    private BigDecimal invoicedOccupiedAmount;
    @ExcelProperty("已认款")
    @Schema(description = "已认款金额")
    private BigDecimal confirmedClaimedAmount;
    @ExcelProperty("未开票应收")
    @Schema(description = "未开票应收")
    private BigDecimal uninvoicedAmount;
    @ExcelProperty("已开票应收")
    @Schema(description = "已开票应收")
    private BigDecimal invoicedArAmount;
    @ExcelProperty("应收合计")
    @Schema(description = "应收合计")
    private BigDecimal arTotalAmount;
    @ExcelProperty("币种")
    @Schema(description = "币种（入表行恒为 CNY）")
    private String currency;
}
