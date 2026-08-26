package cn.iocoder.yudao.module.finance.controller.admin.approver.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 保存公司财务审批人")
@Data
public class FinanceCompanyApproverSaveReqVO {

    @Schema(description = "主体公司部门编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "主体公司不能为空")
    private Long entityCompanyDeptId;

    @Schema(description = "财务审批人用户编号列表")
    private List<Long> userIds;
}
