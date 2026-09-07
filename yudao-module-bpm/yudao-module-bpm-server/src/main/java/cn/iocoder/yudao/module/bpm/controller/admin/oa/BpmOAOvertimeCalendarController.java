package cn.iocoder.yudao.module.bpm.controller.admin.oa;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCalendarPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeCalendarVersionDO;
import cn.iocoder.yudao.module.bpm.service.oa.OaOvertimeCalendar;
import cn.iocoder.yudao.module.bpm.service.oa.OaOvertimeCalendarVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - OA 加班节假日日历")
@RestController
@RequestMapping("/bpm/oa/overtime-calendar")
@Validated
public class BpmOAOvertimeCalendarController {

    @Resource
    private OaOvertimeCalendarVersionService calendarVersionService;

    @GetMapping("/page")
    @Operation(summary = "日历版本分页")
    @PreAuthorize("@ss.hasPermission('bpm:oa-overtime-calendar:query')")
    public CommonResult<PageResult<BpmOAOvertimeCalendarVersionDO>> page(@Valid BpmOAOvertimeCalendarPageReqVO reqVO) {
        return success(calendarVersionService.getPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "日历版本详情")
    @PreAuthorize("@ss.hasPermission('bpm:oa-overtime-calendar:query')")
    public CommonResult<BpmOAOvertimeCalendarVersionDO> get(@RequestParam("id") Long id) {
        return success(calendarVersionService.get(id));
    }

    @PostMapping("/verify")
    @Operation(summary = "核验并启用或驳回日历版本")
    @PreAuthorize("@ss.hasPermission('bpm:oa-overtime-calendar:verify')")
    public CommonResult<Boolean> verify(@RequestParam("id") Long id,
                                        @RequestParam("enable") Boolean enable) {
        calendarVersionService.verifyAndEnable(id, getLoginUserId(), Boolean.TRUE.equals(enable));
        return success(true);
    }

    @PostMapping("/fetch")
    @Operation(summary = "立即抓取")
    @PreAuthorize("@ss.hasPermission('bpm:oa-overtime-calendar:verify')")
    public CommonResult<String> fetch(@RequestParam(value = "year", required = false) Integer year) {
        if (year != null) {
            return success(calendarVersionService.fetchYear(year));
        }
        return success(calendarVersionService.fetchDueYears());
    }

    @GetMapping("/active")
    @Operation(summary = "当前生效日历年份（申请表单只读）")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<OaOvertimeCalendar.YearData> active(@RequestParam("year") Integer year) {
        return success(OaOvertimeCalendar.yearData(year));
    }
}
