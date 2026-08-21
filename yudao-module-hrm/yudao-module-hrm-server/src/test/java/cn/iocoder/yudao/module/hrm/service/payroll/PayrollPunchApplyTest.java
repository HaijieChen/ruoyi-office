package cn.iocoder.yudao.module.hrm.service.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollPunchApplyTest {

    @Test
    void uniqueNameMatchesAndDuplicateGoesUnmatched() {
        PunchXlsParser.ParseResult punch = new PunchXlsParser.ParseResult(
                List.of("姓名"),
                List.of(
                        new PunchXlsParser.PunchRow("张三", "2026/8/10", true, false),
                        new PunchXlsParser.PunchRow("李四", "2026/8/10", false, false)
                ),
                Set.of("张三", "李四", "王五")
        );
        PayrollPunchApply.Result r = PayrollPunchApply.apply(
                List.of(new PayrollPunchApply.EmployeeName(1L, "张三"),
                        new PayrollPunchApply.EmployeeName(2L, "张三")),
                punch);
        assertTrue(r.unmatched().contains("张三") || r.unmatched().contains("王五"));
        assertTrue(r.unmatched().contains("王五"));
    }

    @Test
    void absenceDaysCollectedForUniqueMatch() {
        PunchXlsParser.ParseResult punch = new PunchXlsParser.ParseResult(
                List.of("姓名"),
                List.of(new PunchXlsParser.PunchRow("张三", "2026/8/9", true, false),
                        new PunchXlsParser.PunchRow("张三", "2026/8/10", true, false)),
                Set.of("张三")
        );
        PayrollPunchApply.Result r = PayrollPunchApply.apply(
                List.of(new PayrollPunchApply.EmployeeName(9L, "张三")), punch);
        assertEquals(1, r.matched());
        assertEquals(new BigDecimal("2"), r.absenceByEmployeeId().get(9L));
    }
}
