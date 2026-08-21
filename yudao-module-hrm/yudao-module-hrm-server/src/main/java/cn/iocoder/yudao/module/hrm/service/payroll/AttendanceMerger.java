package cn.iocoder.yudao.module.hrm.service.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merge punch rows with approved processes. Owns R5–R7 attendance classification.
 */
public class AttendanceMerger {

    public static final String SICK = "1";
    public static final String PERSONAL = "2";
    public static final String MARRIAGE = "3";
    public static final String ANNUAL = "4";
    public static final String COMP = "5";
    public static final String OUTING = "outing";
    public static final String TRIP = "trip";

    public record PunchDay(LocalDate date, boolean absence, boolean late, boolean early) {
    }

    public record ProcessCover(LocalDate date, String type, BigDecimal days) {
    }

    public record DayResult(LocalDate date, String status, boolean lateDisplayOnly) {
    }

    public record MonthResult(
            List<DayResult> days,
            BigDecimal sickDays,
            BigDecimal personalLeaveDays,
            BigDecimal absenceDays,
            boolean lateDisplayOnly
    ) {
    }

    public MonthResult merge(List<PunchDay> punches, List<ProcessCover> covers) {
        Map<LocalDate, ProcessCover> coverByDay = new LinkedHashMap<>();
        for (ProcessCover cover : covers) {
            coverByDay.put(cover.date(), cover);
        }
        List<DayResult> days = new ArrayList<>();
        BigDecimal sick = BigDecimal.ZERO;
        BigDecimal personal = BigDecimal.ZERO;
        BigDecimal absence = BigDecimal.ZERO;
        boolean lateOnly = false;
        for (PunchDay punch : punches) {
            ProcessCover cover = coverByDay.get(punch.date());
            if (cover != null) {
                String status = switch (cover.type()) {
                    case SICK -> "SICK";
                    case PERSONAL -> "PERSONAL";
                    default -> "COVERED";
                };
                days.add(new DayResult(punch.date(), status, false));
                BigDecimal d = cover.days() == null ? BigDecimal.ONE : cover.days();
                if (SICK.equals(cover.type())) {
                    sick = sick.add(d);
                } else if (PERSONAL.equals(cover.type())) {
                    personal = personal.add(d);
                }
                continue;
            }
            if (punch.absence()) {
                days.add(new DayResult(punch.date(), "ABSENCE", false));
                absence = absence.add(BigDecimal.ONE);
                continue;
            }
            boolean lateDisplay = punch.late() || punch.early();
            lateOnly = lateOnly || lateDisplay;
            days.add(new DayResult(punch.date(), "WORK", lateDisplay));
        }
        return new MonthResult(days, sick, personal, absence, lateOnly);
    }
}
