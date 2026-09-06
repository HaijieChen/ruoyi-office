package cn.iocoder.yudao.module.bpm.job.oa;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.service.oa.OaOvertimeCalendarVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * XXL 关闭时（test）用 Spring 调度执行同一抓取。与 {@link OaOvertimeCalendarFetchJob} 互斥。
 */
@Component
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "false")
@Slf4j
public class OaOvertimeCalendarFetchScheduledJob {

    @Resource
    private OaOvertimeCalendarVersionService calendarVersionService;

    @Value("${bpm.oa.overtime-calendar.schedule-probe-year:0}")
    private int scheduleProbeYear;

    @Scheduled(cron = "${bpm.oa.overtime-calendar.fetch-cron:0 0 9 ? * MON}", zone = "Asia/Shanghai")
    public void execute() {
        TenantUtils.execute(1L, () -> {
            String result = calendarVersionService.fetchDueYears();
            if (scheduleProbeYear > 0) {
                result += calendarVersionService.fetchYear(scheduleProbeYear);
            }
            log.info("[oaOvertimeCalendarFetchScheduledJob] {}", result);
        });
    }
}
