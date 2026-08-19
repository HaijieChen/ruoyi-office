package cn.iocoder.yudao.module.finance.controller.admin.allocation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Schema(description = "管理后台 - 部门费用分摊导入 Response VO")
@Data
@Builder
public class FinanceDeptCostAllocationImportRespVO {
    @Schema(description = "成功写入行数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer createdCount;
}
