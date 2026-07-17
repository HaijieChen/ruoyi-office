package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - BPM 表单数据源版本保存草稿 Request VO")
@Data
public class BpmFormDataSourceVersionSaveReqVO {

    @Schema(description = "数据源配置 JSON", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "{\"sql\":\"SELECT id, name FROM oa_seal WHERE status = :status\"}")
    @NotBlank(message = "数据源配置不能为空")
    private String sourceConfig;

    @Schema(description = "参数 Schema JSON", example = "[{\"name\":\"status\",\"type\":\"INTEGER\"}]")
    private String parameterSchema;

    @Schema(description = "结果 Schema JSON", example = "[{\"name\":\"id\",\"type\":\"LONG\"}]")
    private String resultSchema;

    @Schema(description = "标签字段名", example = "name")
    @Size(max = 63, message = "标签字段名长度不能超过 63 个字符")
    private String labelField;

    @Schema(description = "值字段名", example = "id")
    @Size(max = 63, message = "值字段名长度不能超过 63 个字符")
    private String valueField;

    @Schema(description = "是否分页", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    @NotNull(message = "是否分页不能为空")
    private Boolean pageable;

    @Schema(description = "最大返回行数，未填写时使用平台默认值", example = "200")
    @Positive(message = "最大返回行数必须大于 0")
    private Integer maxRows;

    @Schema(description = "执行超时秒数，未填写时使用平台默认值", example = "3")
    @Positive(message = "执行超时秒数必须大于 0")
    private Integer timeoutSeconds;

    @Schema(description = "缓存秒数，0 表示不缓存", example = "60")
    @PositiveOrZero(message = "缓存秒数不能小于 0")
    private Integer cacheSeconds;

}
