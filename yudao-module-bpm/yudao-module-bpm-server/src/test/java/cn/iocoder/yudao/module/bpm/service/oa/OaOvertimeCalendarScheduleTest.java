package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OaOvertimeCalendarScheduleTest {

    @Test
    void octToDec_weeklyChecksCurrentAndNextYear() {
        assertEquals(Set.of(2026, 2027), OaOvertimeCalendarSchedule.targetYears(LocalDate.of(2026, 10, 5)));
        assertEquals(Set.of(2026, 2027), OaOvertimeCalendarSchedule.targetYears(LocalDate.of(2026, 12, 28)));
    }

    @Test
    void janToSep_onlyFirstWeekChecksCurrentYear() {
        assertEquals(Set.of(2026), OaOvertimeCalendarSchedule.targetYears(LocalDate.of(2026, 3, 2)));
        assertTrue(OaOvertimeCalendarSchedule.targetYears(LocalDate.of(2026, 3, 16)).isEmpty());
        assertTrue(OaOvertimeCalendarSchedule.targetYears(LocalDate.of(2026, 9, 14)).isEmpty());
    }
}
