package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 产品毛利表分页")
@Data
public class FinanceGrossMarginReportPageRespVO {

    private Long total;
    private List<FinanceGrossMarginReportRespVO> list;
    private Long excludedNonCnyCount;
}
