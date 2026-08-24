package cn.iocoder.yudao.module.hrm.service.payroll;

import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class PayrollAttendanceQuery {

    @Resource
    private JdbcTemplate jdbcTemplate;

    public List<AttendanceMerger.ProcessCover> covers(Long userId, int yearMonth) {
        if (userId == null) {
            return List.of();
        }
        LocalDate monthStart = LocalDate.of(yearMonth / 100, yearMonth % 100, 1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        List<AttendanceMerger.ProcessCover> covers = new ArrayList<>();
        covers.addAll(loadRange(
                "SELECT type, start_time, end_time FROM bpm_oa_leave WHERE deleted = 0 AND result = 2 AND user_id = ?",
                userId, monthStart, monthEnd, row -> String.valueOf(row)));
        covers.addAll(loadRange(
                "SELECT start_time, end_time FROM bpm_oa_business_trip WHERE deleted = 0 AND status = 2 AND user_id = ?",
                userId, monthStart, monthEnd, row -> AttendanceMerger.TRIP));
        covers.addAll(loadRange(
                "SELECT start_time, end_time FROM bpm_oa_outing WHERE deleted = 0 AND status = 2 AND user_id = ?",
                userId, monthStart, monthEnd, row -> AttendanceMerger.OUTING));
        return covers;
    }

    public BigDecimal yearToDateSickBefore(Long userId, int yearMonth) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        LocalDate yearStart = LocalDate.of(yearMonth / 100, 1, 1);
        LocalDate monthStart = LocalDate.of(yearMonth / 100, yearMonth % 100, 1);
        List<AttendanceMerger.ProcessCover> covers = loadRange(
                "SELECT type, start_time, end_time FROM bpm_oa_leave WHERE deleted = 0 AND result = 2 AND user_id = ? AND type = 1",
                userId, yearStart, monthStart.minusDays(1), row -> AttendanceMerger.SICK);
        return covers.stream().map(AttendanceMerger.ProcessCover::days).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<AttendanceMerger.ProcessCover> loadRange(String sql, Long userId,
                                                          LocalDate rangeStart, LocalDate rangeEnd,
                                                          TypeFn typeFn) {
        if (rangeEnd.isBefore(rangeStart)) {
            return List.of();
        }
        boolean hasType = sql.contains("type,");
        return jdbcTemplate.query(sql, ps -> ps.setLong(1, userId), rs -> {
            List<AttendanceMerger.ProcessCover> out = new ArrayList<>();
            while (rs.next()) {
                String type = hasType ? typeFn.apply(rs.getObject(1)) : typeFn.apply(null);
                Timestamp startTs = rs.getTimestamp(hasType ? 2 : 1);
                Timestamp endTs = rs.getTimestamp(hasType ? 3 : 2);
                if (startTs == null || endTs == null) {
                    continue;
                }
                LocalDate start = startTs.toLocalDateTime().toLocalDate();
                LocalDate end = endTs.toLocalDateTime().toLocalDate();
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    if (d.isBefore(rangeStart) || d.isAfter(rangeEnd)) {
                        continue;
                    }
                    if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        continue;
                    }
                    out.add(new AttendanceMerger.ProcessCover(d, type, BigDecimal.ONE));
                }
            }
            return out;
        });
    }

    @FunctionalInterface
    private interface TypeFn {
        String apply(Object raw);
    }
}
