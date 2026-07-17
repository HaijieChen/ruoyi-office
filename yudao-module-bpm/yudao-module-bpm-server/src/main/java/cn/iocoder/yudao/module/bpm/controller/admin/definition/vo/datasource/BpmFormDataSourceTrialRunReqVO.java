package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "管理后台 - BPM 表单数据源版本试运行 Request VO")
@Data
public class BpmFormDataSourceTrialRunReqVO {

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "数据源编号不能为空")
    @Positive(message = "数据源编号必须大于 0")
    private Long sourceId;

    @Schema(description = "版本记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "版本记录编号不能为空")
    @Positive(message = "版本记录编号必须大于 0")
    private Long versionId;

    @Schema(description = "试运行参数")
    private Map<String, Object> params = new LinkedHashMap<>();

}
