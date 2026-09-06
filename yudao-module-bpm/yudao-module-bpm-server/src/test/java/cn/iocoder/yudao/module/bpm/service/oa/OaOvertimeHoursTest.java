package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OaOvertimeHours}：按日拆分、2 小时按原始分钟、单日封顶 8、午夜零切片。
 */
class OaOvertimeHoursTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 9, 5, 10, 0, 0);

    @Test
    void calc_returnsEmpty_whenDurationIsOneHour() {
        assertTrue(OaOvertimeHours.calc(TEN, TEN.plusHours(1)).isEmpty());
    }

    @Test
    void calc_returnsEmpty_whenRawMinutesAre119EvenIfRoundedHoursLookLikeTwo() {
        LocalDateTime end = TEN.plusMinutes(119);
        List<OaOvertimeHours.DaySlice> slices = OaOvertimeHours.split(TEN, end);
        assertEquals(1, slices.size());
        assertEquals(0, new BigDecimal("2.0").compareTo(slices.get(0).hours()));
        assertTrue(OaOvertimeHours.calc(TEN, end).isEmpty());
    }

    @Test
    void calc_returnsTwoHours_whenTenToTwelve() {
        Optional<BigDecimal> hours = OaOvertimeHours.calc(TEN, LocalDateTime.of(2026, 9, 5, 12, 0, 0));
        assertTrue(hours.isPresent());
        assertEquals(0, new BigDecimal("2.0").compareTo(hours.get()));
    }

    @Test
    void calc_capsAtEightHours_whenTenToTwentyThree() {
        Optional<BigDecimal> hours = OaOvertimeHours.calc(TEN, LocalDateTime.of(2026, 9, 5, 23, 0, 0));
        assertTrue(hours.isPresent());
        assertEquals(0, new BigDecimal("8.0").compareTo(hours.get()));
    }

    @Test
    void calc_crossMidnightOnePlusOne_isTwoHours() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 5, 23, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 6, 1, 0, 0);
        Optional<BigDecimal> hours = OaOvertimeHours.calc(start, end);
        assertTrue(hours.isPresent());
        assertEquals(0, new BigDecimal("2.0").compareTo(hours.get()));
        List<OaOvertimeHours.DaySlice> slices = OaOvertimeHours.split(start, end);
        assertEquals(2, slices.size());
        assertEquals(LocalDate.of(2026, 9, 5), slices.get(0).day());
        assertEquals(LocalDate.of(2026, 9, 6), slices.get(1).day());
        assertEquals(0, new BigDecimal("1.0").compareTo(slices.get(0).hours()));
        assertEquals(0, new BigDecimal("1.0").compareTo(slices.get(1).hours()));
    }

    @Test
    void split_endExactlyMidnight_doesNotAddNextDayZeroSlice() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 6, 22, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 7, 0, 0, 0);
        List<OaOvertimeHours.DaySlice> slices = OaOvertimeHours.split(start, end);
        assertEquals(1, slices.size());
        assertEquals(LocalDate.of(2026, 9, 6), slices.get(0).day());
        assertEquals(0, new BigDecimal("2.0").compareTo(slices.get(0).hours()));
        Optional<BigDecimal> hours = OaOvertimeHours.calc(start, end);
        assertTrue(hours.isPresent());
        assertEquals(0, new BigDecimal("2.0").compareTo(hours.get()));
    }

    @Test
    void calc_screenshotSpan_sumsCappedDailyHoursNotTooShort() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 6, 9, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 8, 19, 0, 0);
        Optional<BigDecimal> hours = OaOvertimeHours.calc(start, end);
        assertTrue(hours.isPresent());
        assertEquals(0, new BigDecimal("24.0").compareTo(hours.get()));
        assertEquals(3, OaOvertimeHours.split(start, end).size());
    }

    @Test
    void hoursOnDay_splitsExistingCrossDayRow() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 5, 23, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 6, 1, 0, 0);
        assertEquals(0, new BigDecimal("1.0").compareTo(
                OaOvertimeHours.hoursOnDay(start, end, LocalDate.of(2026, 9, 5))));
        assertEquals(0, new BigDecimal("1.0").compareTo(
                OaOvertimeHours.hoursOnDay(start, end, LocalDate.of(2026, 9, 6))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                OaOvertimeHours.hoursOnDay(start, end, LocalDate.of(2026, 9, 7))));
    }

    @Test
    void calc_returnsEmpty_whenStartEqualsEnd() {
        assertTrue(OaOvertimeHours.calc(TEN, TEN).isEmpty());
    }

    @Test
    void calc_roundsHalfUpToOneDecimal() {
        Optional<BigDecimal> hours = OaOvertimeHours.calc(TEN, TEN.plusMinutes(123));
        assertTrue(hours.isPresent());
        assertEquals(0, new BigDecimal("2.1").compareTo(hours.get()));
    }
}
