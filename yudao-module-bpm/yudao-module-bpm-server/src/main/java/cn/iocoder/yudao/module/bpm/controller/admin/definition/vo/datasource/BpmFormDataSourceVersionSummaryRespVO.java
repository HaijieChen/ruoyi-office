package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - BPM 表单数据源版本历史摘要 Response VO")
@Data
public class BpmFormDataSourceVersionSummaryRespVO {

    @Schema(description = "版本记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long id;

    @Schema(description = "数据源编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long dataSourceId;

    @Schema(description = "版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer version;

    @Schema(description = "版本状态：0-已发布，1-草稿", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

}
