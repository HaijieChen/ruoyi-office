package cn.iocoder.yudao.module.bpm.service.oa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OA 加班时长：按 Asia/Shanghai 自然日拆分实际覆盖区间；结束恰好 00:00 不产生下一日零时长。
 * 单日原始区间超过 8 小时按 8 计；申请合计不足 2 小时 empty。不改 {@link OaDurationHours}。
 */
public final class OaOvertimeHours {

    public static final BigDecimal MIN_HOURS = new BigDecimal("2.0");
    public static final int MIN_MINUTES = 120;
    public static final BigDecimal MAX_HOURS = new BigDecimal("8.0");

    public record DaySlice(LocalDate day, LocalDateTime start, LocalDateTime end, BigDecimal hours) {
    }

    private OaOvertimeHours() {
    }

    public static List<DaySlice> split(LocalDateTime start, LocalDateTime end) {
        List<DaySlice> slices = new ArrayList<>();
        if (start == null || end == null || !end.isAfter(start)) {
            return slices;
        }
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime nextMidnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime sliceEnd = end.isBefore(nextMidnight) ? end : nextMidnight;
            if (sliceEnd.isAfter(cursor)) {
                long minutes = Duration.between(cursor, sliceEnd).toMinutes();
                if (minutes > 0) {
                    BigDecimal hours = BigDecimal.valueOf(minutes)
                            .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
                    if (hours.compareTo(BigDecimal.ZERO) > 0) {
                        if (hours.compareTo(MAX_HOURS) > 0) {
                            hours = MAX_HOURS;
                        }
                        slices.add(new DaySlice(cursor.toLocalDate(), cursor, sliceEnd, hours));
                    }
                }
            }
            cursor = sliceEnd;
        }
        return slices;
    }

    public static Optional<BigDecimal> calc(LocalDateTime start, LocalDateTime end) {
        List<DaySlice> slices = split(start, end);
        if (slices.isEmpty()) {
            return Optional.empty();
        }
        if (Duration.between(start, end).toMinutes() < MIN_MINUTES) {
            return Optional.empty();
        }
        BigDecimal total = BigDecimal.ZERO;
        for (DaySlice slice : slices) {
            total = total.add(slice.hours());
        }
        return Optional.of(total);
    }

    public static BigDecimal hoursOnDay(LocalDateTime start, LocalDateTime end, LocalDate day) {
        if (start == null || end == null || day == null) {
            return BigDecimal.ZERO;
        }
        for (DaySlice slice : split(start, end)) {
            if (slice.day().equals(day)) {
                return slice.hours();
            }
        }
        return BigDecimal.ZERO;
    }

}
