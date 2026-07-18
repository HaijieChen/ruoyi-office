package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - BPM 表单数据源定义和首个草稿原子创建 Request VO")
@Data
public class BpmFormDataSourceCreateWithDraftReqVO {

    @Schema(description = "数据源定义", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull(message = "数据源定义不能为空")
    private BpmFormDataSourceSaveReqVO definition;

    @Schema(description = "首个版本草稿", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull(message = "数据源版本不能为空")
    private BpmFormDataSourceVersionSaveReqVO version;

}
