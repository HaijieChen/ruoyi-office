package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - BPM 表单数据源版本 Response VO")
@Data
public class BpmFormDataSourceVersionRespVO {

    @Schema(description = "版本记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long id;

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long dataSourceId;

    @Schema(description = "版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer version;

    @Schema(description = "版本状态：0-已发布，1-草稿", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "数据源配置 JSON", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceConfig;

    @Schema(description = "参数 Schema JSON")
    private String parameterSchema;

    @Schema(description = "结果 Schema JSON")
    private String resultSchema;

    @Schema(description = "标签字段名", example = "name")
    private String labelField;

    @Schema(description = "值字段名", example = "id")
    private String valueField;

    @Schema(description = "是否分页", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean pageable;

    @Schema(description = "最大返回行数", example = "200")
    private Integer maxRows;

    @Schema(description = "执行超时秒数", example = "3")
    private Integer timeoutSeconds;

    @Schema(description = "缓存秒数", example = "60")
    private Integer cacheSeconds;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

}
