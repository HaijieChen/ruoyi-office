package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - BPM 表单数据源执行 Response VO")
@Data
public class BpmFormDataSourceExecuteRespVO {

    @Schema(description = "结果行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Map<String, Object>> rows;

    @Schema(description = "结果总数", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer total;

    @Schema(description = "实际执行的版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer version;

}
