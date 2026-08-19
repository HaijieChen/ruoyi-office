package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 产品毛利表行")
@Data
public class FinanceGrossMarginReportRespVO {

    private String yearMonth;
    private Long deptId;
    private String deptName;
    private String productType;
    private BigDecimal incomeAmount;
    private BigDecimal costAmount;
    private BigDecimal marginAmount;
}
