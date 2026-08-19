package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 应收明细分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceArDetailReportPageReqVO extends PageParam {

    @Schema(description = "主体公司组织部门编号（BO.entityCompanyDeptId）")
    private Long entityCompanyDeptId;

    @Schema(description = "产品类型")
    private String productType;

    @Schema(description = "只看未开票应收 > 0")
    private Boolean uninvoicedOnly;

    @Schema(description = "只看已开票应收 > 0")
    private Boolean invoicedArOnly;

    @Schema(description = "导入人编号（无 query-all 时忽略，强制为登录人）")
    private Long importerId;
}
