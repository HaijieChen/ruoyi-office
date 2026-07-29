package cn.iocoder.yudao.module.finance.controller.admin.invoice.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 开票申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceInvoiceApplicationPageReqVO extends PageParam {

    @Schema(description = "申请单号")
    private String applicationNo;

    @Schema(description = "审批状态")
    private String approvalStatus;

    @Schema(description = "办票状态")
    private Integer issueStatus;

    @Schema(description = "申请人用户编号")
    private Long applicantUserId;

    @Schema(description = "购方名称")
    private String buyerName;

}
