package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - BPM 表单数据源创建/更新 Request VO")
@Data
public class BpmFormDataSourceSaveReqVO {

    @Schema(description = "编号，更新时必填", example = "1024")
    @Positive(message = "数据源编号必须大于 0")
    private Long id;

    @Schema(description = "数据源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "可用印章")
    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 63, message = "数据源名称长度不能超过 63 个字符")
    private String name;

    @Schema(description = "数据源标识，仅支持小写字母、数字和下划线",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "oa_available_seals")
    @NotBlank(message = "数据源标识不能为空")
    @Size(max = 127, message = "数据源标识长度不能超过 127 个字符")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "数据源标识必须以小写字母开头，且只能包含小写字母、数字和下划线")
    private String code;

    @Schema(description = "数据源类型：1-SQL，2-字典，3-平台 API",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Min(value = 1, message = "数据源类型不正确")
    @Max(value = 3, message = "数据源类型不正确")
    @NotNull(message = "数据源类型不能为空")
    private Integer type;

}
