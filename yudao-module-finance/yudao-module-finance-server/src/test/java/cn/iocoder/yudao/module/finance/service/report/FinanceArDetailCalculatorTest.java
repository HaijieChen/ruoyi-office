package cn.iocoder.yudao.module.finance.service.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceArDetailCalculatorTest {

    @Test
    void shouldComputeUninvoicedInvoicedArAndTotalFromSettlementOccupiedClaimed() {
        BigDecimal settlement = new BigDecimal("100");
        BigDecimal occupied = new BigDecimal("40");
        BigDecimal claimed = new BigDecimal("10");

        assertEquals(0, new BigDecimal("60").compareTo(
                FinanceArDetailCalculator.uninvoiced(settlement, occupied)));
        assertEquals(0, new BigDecimal("30").compareTo(
                FinanceArDetailCalculator.invoicedAr(occupied, claimed)));
        assertEquals(0, new BigDecimal("90").compareTo(
                FinanceArDetailCalculator.arTotal(settlement, claimed)));
    }

    @Test
    void shouldFloorNegativeComponentsAtZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                FinanceArDetailCalculator.uninvoiced(new BigDecimal("10"), new BigDecimal("40"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                FinanceArDetailCalculator.invoicedAr(new BigDecimal("10"), new BigDecimal("40"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                FinanceArDetailCalculator.arTotal(new BigDecimal("10"), new BigDecimal("40"))));
    }

    @Test
    void shouldTreatNullAmountsAsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                FinanceArDetailCalculator.uninvoiced(null, null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                FinanceArDetailCalculator.invoicedAr(null, null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                FinanceArDetailCalculator.arTotal(null, null)));
    }

    @Test
    void shouldAcceptOnlyCnyCaseInsensitiveAndExcludeBlank() {
        assertTrue(FinanceArDetailCalculator.isCny("CNY"));
        assertTrue(FinanceArDetailCalculator.isCny("cny"));
        assertTrue(FinanceArDetailCalculator.isCny(" Cny "));
        assertFalse(FinanceArDetailCalculator.isCny(null));
        assertFalse(FinanceArDetailCalculator.isCny(""));
        assertFalse(FinanceArDetailCalculator.isCny("   "));
        assertFalse(FinanceArDetailCalculator.isCny("USD"));
        assertFalse(FinanceArDetailCalculator.isCny("HKD"));
    }
}
