package cn.iocoder.yudao.module.finance.controller.admin.receipt.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 银行到款导入 Response VO")
@Data
@Builder
public class FinanceReceiptImportRespVO {

    @Schema(description = "导入成功的到款流水号数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> receiptNos;

    @Schema(description = "导入失败的行集合，key 为 Excel 数据行号，value 为失败原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<Integer, String> failureRows;

}
