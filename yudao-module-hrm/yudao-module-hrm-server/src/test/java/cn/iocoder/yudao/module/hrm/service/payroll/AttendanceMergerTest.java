package cn.iocoder.yudao.module.hrm.service.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttendanceMergerTest {

    private final AttendanceMerger merger = new AttendanceMerger();

    @Test
    void ae1_outingCoversPunchAbsence() {
        LocalDate day = LocalDate.of(2026, 8, 10);
        AttendanceMerger.MonthResult r = merger.merge(
                List.of(new AttendanceMerger.PunchDay(day, true, false, false)),
                List.of(new AttendanceMerger.ProcessCover(day, AttendanceMerger.OUTING, BigDecimal.ONE))
        );
        assertEquals("COVERED", r.days().get(0).status());
        assertEquals(0, r.absenceDays().compareTo(BigDecimal.ZERO));
    }

    @Test
    void ae2_lateWithoutProcessIsDisplayOnly() {
        LocalDate day = LocalDate.of(2026, 8, 10);
        AttendanceMerger.MonthResult r = merger.merge(
                List.of(new AttendanceMerger.PunchDay(day, false, true, false)),
                List.of()
        );
        assertEquals("WORK", r.days().get(0).status());
        assertTrue(r.lateDisplayOnly());
        assertEquals(0, r.absenceDays().compareTo(BigDecimal.ZERO));
    }

    @Test
    void ae7_annualLeaveCoversAndIsNotPersonal() {
        LocalDate day = LocalDate.of(2026, 8, 11);
        AttendanceMerger.MonthResult r = merger.merge(
                List.of(new AttendanceMerger.PunchDay(day, true, false, false)),
                List.of(new AttendanceMerger.ProcessCover(day, AttendanceMerger.ANNUAL, BigDecimal.ONE))
        );
        assertEquals("COVERED", r.days().get(0).status());
        assertEquals(0, r.personalLeaveDays().compareTo(BigDecimal.ZERO));
        assertFalse(r.lateDisplayOnly());
    }

    @Test
    void uncoveredAbsenceCountsAsAbsence() {
        LocalDate day = LocalDate.of(2026, 8, 9);
        AttendanceMerger.MonthResult r = merger.merge(
                List.of(new AttendanceMerger.PunchDay(day, true, false, false)),
                List.of()
        );
        assertEquals("ABSENCE", r.days().get(0).status());
        assertEquals(0, r.absenceDays().compareTo(BigDecimal.ONE));
    }
}
