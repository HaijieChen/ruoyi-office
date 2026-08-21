package cn.iocoder.yudao.module.hrm.service.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollPunchApplyTest {

    @Test
    void matchByEmployeeNoEvenWhenNamesDuplicate() {
        PunchXlsParser.ParseResult punch = new PunchXlsParser.ParseResult(
                List.of("工号", "姓名"),
                List.of(new PunchXlsParser.PunchRow("E01", "张三", "2026/8/10", true, false)),
                Set.of("张三")
        );
        PayrollPunchApply.Result r = PayrollPunchApply.apply(
                List.of(
                        new PayrollPunchApply.EmployeeName(1L, "张三", "E01"),
                        new PayrollPunchApply.EmployeeName(2L, "张三", "E02")),
                punch);
        assertEquals(1, r.matched());
        assertEquals(new BigDecimal("1"), r.absenceByEmployeeId().get(1L));
        assertTrue(r.unmatched().isEmpty());
    }

    @Test
    void fallbackToUniqueNameWhenEmployeeNoMissing() {
        PunchXlsParser.ParseResult punch = new PunchXlsParser.ParseResult(
                List.of("工号", "姓名"),
                List.of(new PunchXlsParser.PunchRow("", "李四", "2026/8/10", true, false)),
                Set.of("李四")
        );
        PayrollPunchApply.Result r = PayrollPunchApply.apply(
                List.of(new PayrollPunchApply.EmployeeName(9L, "李四", "E09")),
                punch);
        assertEquals(1, r.matched());
        assertEquals(new BigDecimal("1"), r.absenceByEmployeeId().get(9L));
    }

    @Test
    void missingNoAndDuplicateNameUnmatched() {
        PunchXlsParser.ParseResult punch = new PunchXlsParser.ParseResult(
                List.of("工号", "姓名"),
                List.of(new PunchXlsParser.PunchRow("", "张三", "2026/8/10", false, false)),
                Set.of("张三")
        );
        PayrollPunchApply.Result r = PayrollPunchApply.apply(
                List.of(
                        new PayrollPunchApply.EmployeeName(1L, "张三", "E01"),
                        new PayrollPunchApply.EmployeeName(2L, "张三", "E02")),
                punch);
        assertEquals(0, r.matched());
        assertTrue(r.unmatched().contains("姓名=张三"));
    }
}
