package cn.iocoder.yudao.module.hrm.service.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Payable salary for one employee-month. Owns R9–R16 money rules.
 */
public class PayrollCalculator {

    public static final BigDecimal MONTHLY_WORKING_DAYS = new BigDecimal("21.75");
    public static final BigDecimal SOCIAL_RATE = new BigDecimal("0.105");
    public static final BigDecimal HOUSING_RATE = new BigDecimal("0.05");

    public record Input(
            BigDecimal monthlyWage,
            BigDecimal fullAttendanceBonus,
            BigDecimal subsidies,
            BigDecimal overtime,
            BigDecimal otherPlusMinus,
            BigDecimal socialBase,
            BigDecimal housingBase,
            BigDecimal tax,
            BigDecimal sickDays,
            BigDecimal personalLeaveDays,
            BigDecimal absenceDays,
            BigDecimal yearToDateSickBeforeMonth,
            boolean allScheduledDaysSick,
            int tenureYears,
            BigDecimal minWage
    ) {
    }

    public record Result(
            BigDecimal dailyWage,
            BigDecimal sickPay,
            BigDecimal personalLeaveDeduction,
            BigDecimal absenceDeduction,
            BigDecimal bonusPaid,
            BigDecimal payable,
            BigDecimal socialDeduction,
            BigDecimal housingDeduction,
            BigDecimal net
    ) {
    }

    public Result calculate(Input in) {
        BigDecimal daily = in.monthlyWage().divide(MONTHLY_WORKING_DAYS, 8, RoundingMode.HALF_UP);
        BigDecimal unpaidDays = nz(in.personalLeaveDays()).add(nz(in.absenceDays()));
        BigDecimal personalDeduction = daily.multiply(unpaidDays);
        boolean bonus = unpaidDays.compareTo(BigDecimal.ZERO) == 0
                && nz(in.sickDays()).compareTo(BigDecimal.ZERO) == 0;
        BigDecimal bonusPaid = bonus ? nz(in.fullAttendanceBonus()) : BigDecimal.ZERO;
        BigDecimal sickPay = sickPay(in, daily);
        BigDecimal payable = nz(in.monthlyWage())
                .subtract(daily.multiply(nz(in.sickDays())))
                .add(sickPay)
                .add(bonusPaid)
                .add(nz(in.subsidies()))
                .add(nz(in.overtime()))
                .add(nz(in.otherPlusMinus()))
                .subtract(personalDeduction);
        BigDecimal social = nz(in.socialBase()).multiply(SOCIAL_RATE);
        BigDecimal housing = nz(in.housingBase()).multiply(HOUSING_RATE);
        BigDecimal net = payable.subtract(social).subtract(housing).subtract(nz(in.tax()));
        return new Result(
                daily,
                sickPay.setScale(2, RoundingMode.HALF_UP),
                personalDeduction.setScale(2, RoundingMode.HALF_UP),
                daily.multiply(nz(in.absenceDays())).setScale(2, RoundingMode.HALF_UP),
                bonusPaid,
                payable.setScale(2, RoundingMode.HALF_UP),
                social.setScale(2, RoundingMode.HALF_UP),
                housing.setScale(2, RoundingMode.HALF_UP),
                net.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal sickPay(Input in, BigDecimal daily) {
        if (in.allScheduledDaysSick()) {
            return nz(in.minWage());
        }
        BigDecimal remaining = nz(in.sickDays());
        BigDecimal ytd = nz(in.yearToDateSickBeforeMonth());
        BigDecimal pay = BigDecimal.ZERO;
        BigDecimal step = new BigDecimal("0.5");
        BigDecimal tenureRate = tenureRate(in.tenureYears());
        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal chunk = remaining.min(step);
            ytd = ytd.add(chunk);
            BigDecimal rate;
            if (ytd.compareTo(new BigDecimal("30")) >= 0) {
                rate = nz(in.minWage()).divide(MONTHLY_WORKING_DAYS, 8, RoundingMode.HALF_UP)
                        .divide(daily, 8, RoundingMode.HALF_UP);
            } else if (ytd.compareTo(BigDecimal.TEN) > 0) {
                rate = new BigDecimal("0.30");
            } else {
                rate = tenureRate;
            }
            pay = pay.add(daily.multiply(rate).multiply(chunk));
            remaining = remaining.subtract(chunk);
        }
        return pay;
    }

    static BigDecimal tenureRate(int years) {
        if (years < 2) {
            return new BigDecimal("0.60");
        }
        if (years < 4) {
            return new BigDecimal("0.70");
        }
        if (years < 6) {
            return new BigDecimal("0.80");
        }
        if (years < 8) {
            return new BigDecimal("0.90");
        }
        return BigDecimal.ONE;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
