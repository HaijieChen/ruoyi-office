package cn.iocoder.yudao.module.finance.controller.admin.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 合同签约导入 Response VO")
@Data
@Builder
public class FinanceContractApplicationImportRespVO {
    @Schema(description = "成功写入的合同业务单号", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> createdNos;
    @Schema(description = "失败行，key 为 Excel 数据行号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<Integer, String> failureRows;
}
