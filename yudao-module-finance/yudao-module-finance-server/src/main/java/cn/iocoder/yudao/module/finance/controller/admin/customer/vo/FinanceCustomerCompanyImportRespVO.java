package cn.iocoder.yudao.module.finance.controller.admin.customer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 客户公司导入 Response VO")
@Data
@Builder
public class FinanceCustomerCompanyImportRespVO {
    @Schema(description = "成功写入的客户公司编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> createdCodes;
    @Schema(description = "失败行，key 为 Excel 数据行号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<Integer, String> failureRows;
}
