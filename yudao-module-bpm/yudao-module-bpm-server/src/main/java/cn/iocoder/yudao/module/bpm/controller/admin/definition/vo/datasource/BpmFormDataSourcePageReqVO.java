package cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - BPM 表单数据源分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class BpmFormDataSourcePageReqVO extends PageParam {

    @Schema(description = "数据源名称", example = "可用印章")
    private String name;

    @Schema(description = "数据源标识", example = "oa_available_seals")
    private String code;

    @Schema(description = "数据源类型：1-SQL，2-字典，3-平台 API", example = "1")
    private Integer type;

    @Schema(description = "状态：0-开启，1-关闭", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
