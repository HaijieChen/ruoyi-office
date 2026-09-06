package cn.iocoder.yudao.module.bpm.controller.admin.oa.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 加班节假日日历分页")
@Data
@EqualsAndHashCode(callSuper = true)
public class BpmOAOvertimeCalendarPageReqVO extends PageParam {

    private Integer calendarYear;
    private String status;
}
