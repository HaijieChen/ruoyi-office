package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 部门利润表查询")
@Data
public class FinanceDeptProfitReportReqVO {

    @Schema(description = "开始月份 YYYY-MM")
    private String fromMonth;
    @Schema(description = "结束月份 YYYY-MM")
    private String toMonth;
}
