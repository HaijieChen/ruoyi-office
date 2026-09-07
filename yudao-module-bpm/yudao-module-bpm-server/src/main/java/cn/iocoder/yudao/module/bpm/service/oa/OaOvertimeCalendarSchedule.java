package cn.iocoder.yudao.module.bpm.service.oa;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

/**
 * 10–12 月每周检查当年+下一年；其余月份仅每月第一周检查已生效年更正。
 */
public final class OaOvertimeCalendarSchedule {

    private OaOvertimeCalendarSchedule() {
    }

    public static Set<Integer> targetYears(LocalDate today) {
        TreeSet<Integer> years = new TreeSet<>();
        int year = today.getYear();
        int month = today.getMonthValue();
        if (month >= 10) {
            years.add(year);
            years.add(year + 1);
            return years;
        }
        if (today.getDayOfMonth() <= 7) {
            years.add(year);
        }
        return years;
    }
}
