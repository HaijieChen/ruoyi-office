package cn.iocoder.yudao.module.finance.controller.admin.business.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 商务签单导入 Response VO")
@Data
@Builder
public class FinanceBusinessOrderImportRespVO {

    @Schema(description = "导入成功的商务签单号数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> orderNos;

    @Schema(description = "导入失败的行集合，key 为 Excel 数据行号，value 为失败原因", requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<Integer, String> failureRows;

    @Schema(description = "因重复而跳过的行号数组", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Integer> skippedRows;

}
