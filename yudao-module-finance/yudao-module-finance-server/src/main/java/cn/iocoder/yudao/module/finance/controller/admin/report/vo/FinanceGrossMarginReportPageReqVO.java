package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 产品毛利表查询")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceGrossMarginReportPageReqVO extends PageParam {

    @Schema(description = "开始月份 YYYY-MM")
    private String fromMonth;
    @Schema(description = "结束月份 YYYY-MM")
    private String toMonth;
    @Schema(description = "部门编号，0=未分配")
    private Long deptId;
    @Schema(description = "产品类型")
    private String productType;
}
