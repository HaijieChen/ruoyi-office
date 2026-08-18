package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OaDurationHours} 时长换算：分钟 / 60，HALF_UP 保留 1 位小数。
 */
class OaDurationHoursTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 18, 9, 0, 0);

    @Test
    void calc_returnsOnePointFiveHours_whenDurationIsNinetyMinutes() {
        Optional<BigDecimal> hours = OaDurationHours.calc(START, START.plusMinutes(90));

        assertTrue(hours.isPresent());
        assertEquals(0, new BigDecimal("1.5").compareTo(hours.get()));
    }

    @Test
    void calc_returnsZeroPointOneHours_whenDurationIsSixMinutes() {
        Optional<BigDecimal> hours = OaDurationHours.calc(START, START.plusMinutes(6));

        assertTrue(hours.isPresent());
        assertEquals(0, new BigDecimal("0.1").compareTo(hours.get()));
    }

    @Test
    void calc_returnsEmpty_whenStartAndEndAreEqual() {
        assertTrue(OaDurationHours.calc(START, START).isEmpty());
    }

    @Test
    void calc_returnsEmpty_whenEndIsBeforeStart() {
        assertTrue(OaDurationHours.calc(START, START.minusMinutes(30)).isEmpty());
    }

    @Test
    void calc_returnsEmpty_whenRoundedHoursIsZero() {
        assertTrue(OaDurationHours.calc(START, START.plusMinutes(1)).isEmpty());
    }

}
