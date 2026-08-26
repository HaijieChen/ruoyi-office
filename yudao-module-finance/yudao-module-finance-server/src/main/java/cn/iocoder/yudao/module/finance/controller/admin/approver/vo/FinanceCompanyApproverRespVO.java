package cn.iocoder.yudao.module.finance.controller.admin.approver.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 公司财务审批人")
@Data
public class FinanceCompanyApproverRespVO {

    @Schema(description = "主体公司部门编号")
    private Long entityCompanyDeptId;

    @Schema(description = "主体公司名称")
    private String entityCompanyName;

    @Schema(description = "财务审批人用户编号")
    private List<Long> userIds;

    @Schema(description = "财务审批人姓名")
    private List<String> userNames;
}
