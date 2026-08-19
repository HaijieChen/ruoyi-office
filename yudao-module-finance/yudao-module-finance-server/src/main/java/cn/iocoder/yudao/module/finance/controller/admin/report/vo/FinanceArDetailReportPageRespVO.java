package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 应收明细分页 Response VO")
@Data
public class FinanceArDetailReportPageRespVO {

    @Schema(description = "总量")
    private Long total;
    @Schema(description = "数据")
    private List<FinanceArDetailReportRespVO> list;
    @Schema(description = "因非人民币被排除的行数")
    private Long excludedNonCnyCount;
}
