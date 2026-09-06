package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.bpm.service.oa.OaOvertimeCalendar.DayKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026 法定日按国令第795号独立枚举（13天），不用「等于种子文件」自证。
 */
class OaOvertimeCalendarTest {

    /** 元旦 1 天：1月1日 */
    private static final List<LocalDate> YUANDAN = List.of(LocalDate.of(2026, 1, 1));
    /** 春节 4 天：2026 除夕=2-16 至初三=2-19 */
    private static final List<LocalDate> SPRING = List.of(
            LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17),
            LocalDate.of(2026, 2, 18), LocalDate.of(2026, 2, 19));
    /** 清明 1 天：2026-04-04 */
    private static final List<LocalDate> QINGMING = List.of(LocalDate.of(2026, 4, 4));
    /** 劳动节 2 天：5月1日、2日（不是 5月3日） */
    private static final List<LocalDate> LABOR = List.of(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2));
    /** 端午 1 天：2026-06-19 */
    private static final List<LocalDate> DRAGON = List.of(LocalDate.of(2026, 6, 19));
    /** 中秋 1 天：2026-09-25 */
    private static final List<LocalDate> MOON = List.of(LocalDate.of(2026, 9, 25));
    /** 国庆 3 天：10月1日至3日 */
    private static final List<LocalDate> NATIONAL = List.of(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3));

    @Test
    void legalHolidays_areThirteenDistinctDaysFrom795Order() {
        List<LocalDate> all = new java.util.ArrayList<>();
        all.addAll(YUANDAN);
        all.addAll(SPRING);
        all.addAll(QINGMING);
        all.addAll(LABOR);
        all.addAll(DRAGON);
        all.addAll(MOON);
        all.addAll(NATIONAL);
        assertEquals(13, all.size());
        assertEquals(13, Set.copyOf(all).size());
        for (LocalDate day : all) {
            assertEquals(DayKind.LEGAL_HOLIDAY, OaOvertimeCalendar.kind(day), day.toString());
            assertTrue(OaOvertimeCalendar.allowed(day), day.toString());
        }
    }

    @Test
    void laborDay_may3_isOrdinaryWeekendNotLegalHoliday() {
        LocalDate may3 = LocalDate.of(2026, 5, 3);
        assertEquals(DayKind.WEEKEND, OaOvertimeCalendar.kind(may3));
        assertTrue(OaOvertimeCalendar.allowed(may3));
        assertFalse(OaOvertimeCalendar.legalHolidaySet(2026).contains("2026-05-03"));
    }

    @Test
    void weekendSunday_isAllowedAsWeekend() {
        assertEquals(DayKind.WEEKEND, OaOvertimeCalendar.kind(LocalDate.of(2026, 9, 6)));
        assertEquals(DayKind.WEEKEND, OaOvertimeCalendar.kind(LocalDate.of(2026, 9, 5)));
        assertTrue(OaOvertimeCalendar.allowed(LocalDate.of(2026, 9, 6)));
    }

    @Test
    void weekday_isForbidden() {
        LocalDate mon = LocalDate.of(2026, 9, 7);
        LocalDate tue = LocalDate.of(2026, 9, 8);
        assertEquals(DayKind.WEEKDAY, OaOvertimeCalendar.kind(mon));
        assertEquals(DayKind.WEEKDAY, OaOvertimeCalendar.kind(tue));
        assertFalse(OaOvertimeCalendar.allowed(mon));
        assertEquals(Set.of(mon, tue),
                OaOvertimeCalendar.forbiddenDays(List.of(
                        LocalDate.of(2026, 9, 6), mon, tue)));
    }

    @Test
    void makeupWorkday_sunday_isForbidden() {
        LocalDate sep20 = LocalDate.of(2026, 9, 20);
        assertEquals(DayKind.MAKEUP_WORKDAY, OaOvertimeCalendar.kind(sep20));
        assertFalse(OaOvertimeCalendar.allowed(sep20));
    }

    @Test
    void makeupRestWeekday_isForbiddenAndNotExpanded() {
        LocalDate feb20 = LocalDate.of(2026, 2, 20);
        assertEquals(DayKind.MAKEUP_REST, OaOvertimeCalendar.kind(feb20));
        assertFalse(OaOvertimeCalendar.allowed(feb20));
        assertEquals(DayKind.MAKEUP_REST, OaOvertimeCalendar.kind(LocalDate.of(2026, 1, 2)));
        assertEquals(DayKind.MAKEUP_REST, OaOvertimeCalendar.kind(LocalDate.of(2026, 4, 6)));
    }

    @Test
    void missingYear_throwsCalendarMissing() {
        OaOvertimeCalendar.CalendarMissingException ex = assertThrows(
                OaOvertimeCalendar.CalendarMissingException.class,
                () -> OaOvertimeCalendar.kind(LocalDate.of(2027, 1, 1)));
        assertEquals(2027, ex.year());
        assertFalse(OaOvertimeCalendar.hasYear(2027));
    }

    @Test
    void fourKindsAreStoredSeparatelyOnSeed() {
        OaOvertimeCalendar.YearData data = OaOvertimeCalendar.yearData(2026);
        assertEquals("国办发明电〔2025〕7号", data.source);
        assertTrue(data.sourceUrl.contains("7047091"));
        assertEquals(13, data.legalHolidays.size());
        assertEquals(6, data.makeupWorkdays.size());
        assertFalse(data.makeupRestDays.isEmpty());
        assertFalse(data.weekends.isEmpty());
        assertTrue(data.weekends.contains("2026-09-06"));
        assertFalse(data.weekends.contains("2026-09-20"));
        assertFalse(data.legalHolidays.contains("2026-05-03"));
    }
}
