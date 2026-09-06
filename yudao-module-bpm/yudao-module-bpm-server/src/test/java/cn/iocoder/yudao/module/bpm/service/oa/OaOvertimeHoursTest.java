package cn.iocoder.yudao.module.bpm.service.oa;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OaOvertimeHours}：同日、最少 2 小时、最多 8 小时。
 */
class OaOvertimeHoursTest {

    private static final LocalDateTime TEN = LocalDateTime.of(2026, 9, 5, 10, 0, 0);

    @Test
    void calc_returnsEmpty_whenDurationIsOneHour() {
        assertTrue(OaOvertimeHours.calc(TEN, TEN.plusHours(1)).isEmpty());
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
    void calc_returnsEmpty_whenCrossesMidnight() {
        assertTrue(OaOvertimeHours.calc(TEN, TEN.plusDays(1)).isEmpty());
    }

    @Test
    void calc_returnsEmpty_whenStartEqualsEnd() {
        assertTrue(OaOvertimeHours.calc(TEN, TEN).isEmpty());
    }

}
