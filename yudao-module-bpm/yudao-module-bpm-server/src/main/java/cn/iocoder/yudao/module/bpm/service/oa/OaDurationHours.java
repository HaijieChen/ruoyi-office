package cn.iocoder.yudao.module.bpm.service.oa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * OA 出差 / 外出时长：hours = minutes / 60，HALF_UP 保留 1 位小数。
 * 结束时间不晚于开始，或换算后 hours &lt;= 0 时返回 empty，不抛异常。
 */
public final class OaDurationHours {

    private OaDurationHours() {
    }

    public static Optional<BigDecimal> calc(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            return Optional.empty();
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            return Optional.empty();
        }
        BigDecimal hours = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
        if (hours.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(hours);
    }

}
