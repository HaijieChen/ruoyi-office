package cn.iocoder.yudao.module.bpm.service.oa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * OA 加班时长：同一自然日；分钟/60 HALF_UP 一位小数；不足 2 小时 empty；超过 8 小时按 8 封顶。
 * 不改 {@link OaDurationHours}（外出/出差）。
 */
public final class OaOvertimeHours {

    public static final BigDecimal MIN_HOURS = new BigDecimal("2.0");
    public static final BigDecimal MAX_HOURS = new BigDecimal("8.0");

    private OaOvertimeHours() {
    }

    public static Optional<BigDecimal> calc(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return Optional.empty();
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            return Optional.empty();
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            return Optional.empty();
        }
        BigDecimal hours = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
        if (hours.compareTo(MIN_HOURS) < 0) {
            return Optional.empty();
        }
        if (hours.compareTo(MAX_HOURS) > 0) {
            return Optional.of(MAX_HOURS);
        }
        return Optional.of(hours);
    }

}
