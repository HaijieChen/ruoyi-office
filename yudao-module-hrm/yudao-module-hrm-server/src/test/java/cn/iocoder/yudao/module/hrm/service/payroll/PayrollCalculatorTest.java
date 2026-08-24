package cn.iocoder.yudao.module.hrm.service.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollCalculatorTest {

    private final PayrollCalculator calculator = new PayrollCalculator();

    @Test
    void ae3_personalLeaveDeductsDailyAndDropsBonus() {
        PayrollCalculator.Result r = calculator.calculate(base(8700).personalLeaveDays(new BigDecimal("1")).build());
        assertEquals(new BigDecimal("400.00"), r.personalLeaveDeduction());
        assertEquals(0, r.bonusPaid().compareTo(BigDecimal.ZERO));
    }

    @Test
    void ae4_firstSickDaysUseTenureRate() {
        PayrollCalculator.Result r = calculator.calculate(base(8700)
                .sickDays(new BigDecimal("3"))
                .tenureYears(1)
                .yearToDateSickBeforeMonth(BigDecimal.ZERO)
                .build());
        assertEquals(new BigDecimal("720.00"), r.sickPay());
        assertEquals(new BigDecimal("480.00"), r.sickDeduction());
        assertEquals(0, r.bonusPaid().compareTo(BigDecimal.ZERO));
    }

    @Test
    void ae5_days11And12PayThirtyPercent() {
        PayrollCalculator.Result r = calculator.calculate(base(8700)
                .sickDays(new BigDecimal("2"))
                .yearToDateSickBeforeMonth(new BigDecimal("10"))
                .tenureYears(1)
                .build());
        assertEquals(new BigDecimal("240.00"), r.sickPay());
    }

    @Test
    void ae6_fullMonthSickPaysMinWage() {
        PayrollCalculator.Result r = calculator.calculate(base(8700)
                .allScheduledDaysSick(true)
                .sickDays(new BigDecimal("22"))
                .minWage(new BigDecimal("2690"))
                .build());
        assertEquals(new BigDecimal("2690.00"), r.sickPay());
        assertEquals(new BigDecimal("6010.00"), r.sickDeduction());
        assertEquals(new BigDecimal("2690.00"), r.payable());
    }

    @Test
    void ae9_basesStayOutOfGross() {
        PayrollCalculator.Result r = calculator.calculate(base(8000)
                .socialBase(new BigDecimal("8000"))
                .housingBase(new BigDecimal("8000"))
                .fullAttendanceBonus(new BigDecimal("200"))
                .build());
        assertEquals(new BigDecimal("8200.00"), r.payable());
        assertTrue(r.payable().compareTo(new BigDecimal("16000")) < 0);
    }

    @Test
    void paidLeaveDoesNotDropBonus() {
        PayrollCalculator.Result r = calculator.calculate(base(8000)
                .fullAttendanceBonus(new BigDecimal("200"))
                .build());
        assertEquals(new BigDecimal("200"), r.bonusPaid());
    }

    private static InputBuilder base(int wage) {
        return new InputBuilder(new BigDecimal(wage));
    }

    private static final class InputBuilder {
        private final BigDecimal wage;
        private BigDecimal bonus = BigDecimal.ZERO;
        private BigDecimal subsidies = BigDecimal.ZERO;
        private BigDecimal overtime = BigDecimal.ZERO;
        private BigDecimal other = BigDecimal.ZERO;
        private BigDecimal socialBase = BigDecimal.ZERO;
        private BigDecimal housingBase = BigDecimal.ZERO;
        private BigDecimal tax = BigDecimal.ZERO;
        private BigDecimal sick = BigDecimal.ZERO;
        private BigDecimal personal = BigDecimal.ZERO;
        private BigDecimal absence = BigDecimal.ZERO;
        private BigDecimal ytd = BigDecimal.ZERO;
        private boolean allSick;
        private int tenure;
        private BigDecimal minWage = BigDecimal.ZERO;

        private InputBuilder(BigDecimal wage) {
            this.wage = wage;
        }

        InputBuilder fullAttendanceBonus(BigDecimal v) { bonus = v; return this; }
        InputBuilder socialBase(BigDecimal v) { socialBase = v; return this; }
        InputBuilder housingBase(BigDecimal v) { housingBase = v; return this; }
        InputBuilder sickDays(BigDecimal v) { sick = v; return this; }
        InputBuilder personalLeaveDays(BigDecimal v) { personal = v; return this; }
        InputBuilder yearToDateSickBeforeMonth(BigDecimal v) { ytd = v; return this; }
        InputBuilder allScheduledDaysSick(boolean v) { allSick = v; return this; }
        InputBuilder tenureYears(int v) { tenure = v; return this; }
        InputBuilder minWage(BigDecimal v) { minWage = v; return this; }

        PayrollCalculator.Input build() {
            return new PayrollCalculator.Input(wage, bonus, subsidies, overtime, other,
                    socialBase, housingBase, tax, sick, personal, absence, ytd, allSick, tenure, minWage);
        }
    }
}
