package cn.iocoder.yudao.module.bpm.controller.admin.oa.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 外出申请分页 Request VO")
@Data
public class BpmOAOutingPageReqVO extends PageParam {

    @Schema(description = "状态", example = "1")
    private Integer status;

    @Schema(description = "外出事由，模糊匹配", example = "客户拜访")
    private String reason;

    @Schema(description = "外出地点，模糊匹配", example = "上海")
    private String location;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @Schema(description = "申请时间")
    private LocalDateTime[] createTime;

}
