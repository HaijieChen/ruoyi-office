package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 商务单分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceBusinessOrderPageReqVO extends PageParam {

    @Schema(description = "商务单号")
    private String orderNo;
    @Schema(description = "业务主体/客户")
    private String businessSubject;
    @Schema(description = "业务类型")
    private String businessType;
    @Schema(description = "合同引用")
    private String contractRef;
    @Schema(description = "项目引用")
    private String projectRef;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "币种")
    private String currency;

}
