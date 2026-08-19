package cn.iocoder.yudao.module.finance.service.report;

import java.math.BigDecimal;

/**
 * 应收明细口径：未开票 / 已开票应收 / 合计。金额空按 0。
 */
public final class FinanceArDetailCalculator {

    private FinanceArDetailCalculator() {
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    public static BigDecimal uninvoiced(BigDecimal settlement, BigDecimal invoicedOccupied) {
        return maxZero(nz(settlement).subtract(nz(invoicedOccupied)));
    }

    public static BigDecimal invoicedAr(BigDecimal invoicedOccupied, BigDecimal confirmedClaimed) {
        return maxZero(nz(invoicedOccupied).subtract(nz(confirmedClaimed)));
    }

    public static BigDecimal arTotal(BigDecimal settlement, BigDecimal confirmedClaimed) {
        return maxZero(nz(settlement).subtract(nz(confirmedClaimed)));
    }

    /** 仅 CNY 入表；大小写不敏感，trim 后比较；null/空白/其它币种排除。 */
    public static boolean isCny(String currency) {
        return currency != null && "CNY".equalsIgnoreCase(currency.trim());
    }

    /** 与商务单 blank-aware dual-read 一致：优先非空 snapshot。 */
    public static String effectiveProductType(String productTypeSnapshot, String productName) {
        if (productTypeSnapshot != null && !productTypeSnapshot.isBlank()) {
            return productTypeSnapshot.trim();
        }
        if (productName != null && !productName.isBlank()) {
            return productName.trim();
        }
        return productName;
    }
}
