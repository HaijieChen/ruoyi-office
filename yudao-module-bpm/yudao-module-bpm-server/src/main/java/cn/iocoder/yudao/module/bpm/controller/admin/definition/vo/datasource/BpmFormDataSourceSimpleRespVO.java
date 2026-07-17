package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - BPM 表单数据源精简 Response VO")
@Data
public class BpmFormDataSourceSimpleRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "可用印章")
    private String name;

    @Schema(description = "数据源标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "oa_available_seals")
    private String code;

    @Schema(description = "数据源类型：1-SQL，2-字典，3-平台 API",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer type;

    @Schema(description = "已发布版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer publishedVersion;

}
