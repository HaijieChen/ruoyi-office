package cn.iocoder.yudao.module.finance.controller.admin.customer.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 客户公司分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class FinanceCustomerCompanyPageReqVO extends PageParam {

    @Schema(description = "名称")
    private String name;

    @Schema(description = "纳税人识别号")
    private String taxNo;

    @Schema(description = "编码")
    private String code;

    @Schema(description = "状态：0启用 1停用")
    private Integer status;

}
