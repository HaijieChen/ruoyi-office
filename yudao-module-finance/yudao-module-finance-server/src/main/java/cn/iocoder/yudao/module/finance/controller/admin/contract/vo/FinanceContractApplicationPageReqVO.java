package cn.iocoder.yudao.module.finance.controller.admin.contract.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 合同签约申请分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FinanceContractApplicationPageReqVO extends PageParam {

    @Schema(description = "业务单号")
    private String applicationNo;

    @Schema(description = "审批状态")
    private String approvalStatus;

    @Schema(description = "申请人")
    private Long applicantUserId;

    @Schema(description = "签约主体")
    private String signCompany;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "对方名称")
    private String counterpartyName;
}
