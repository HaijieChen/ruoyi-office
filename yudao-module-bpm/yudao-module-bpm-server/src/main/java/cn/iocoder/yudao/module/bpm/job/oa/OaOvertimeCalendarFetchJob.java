package cn.iocoder.yudao.module.bpm.job.oa;

import cn.iocoder.yudao.module.bpm.service.oa.OaOvertimeCalendarVersionService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OaOvertimeCalendarFetchJob {

    @Resource
    private OaOvertimeCalendarVersionService calendarVersionService;

    @XxlJob("oaOvertimeCalendarFetchJob")
    public String execute() {
        String result = calendarVersionService.fetchDueYears();
        log.info("[oaOvertimeCalendarFetchJob] {}", result);
        return result;
    }
}
