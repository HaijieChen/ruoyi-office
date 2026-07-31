package cn.iocoder.yudao.module.finance.service.receipt;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 档 1：yyyy-MM-dd、yyyy-MM-dd HH:mm:ss；空白 / 无法解析可区分。
 */
class FinanceReceiptImportDateParserTest {

    @Test
    void parseDateOnlyShouldUseStartOfDay() {
        assertEquals(LocalDateTime.of(2026, 5, 25, 0, 0, 0),
                FinanceReceiptImportDateParser.tryParse("2026-05-25"));
    }

    @Test
    void parseDateTimeShouldKeepTime() {
        assertEquals(LocalDateTime.of(2026, 5, 25, 10, 15, 30),
                FinanceReceiptImportDateParser.tryParse("2026-05-25 10:15:30"));
    }

    @Test
    void parseShouldTrimWhitespace() {
        assertEquals(LocalDateTime.of(2026, 5, 25, 0, 0, 0),
                FinanceReceiptImportDateParser.tryParse("  2026-05-25  "));
    }

    @Test
    void parseBlankShouldReturnNull() {
        assertNull(FinanceReceiptImportDateParser.tryParse(null));
        assertNull(FinanceReceiptImportDateParser.tryParse(""));
        assertNull(FinanceReceiptImportDateParser.tryParse("   "));
    }

    @Test
    void parseInvalidShouldReturnNull() {
        assertNull(FinanceReceiptImportDateParser.tryParse("not-a-date"));
        assertNull(FinanceReceiptImportDateParser.tryParse("2026-13-01"));
        assertNull(FinanceReceiptImportDateParser.tryParse("25/05/2026"));
        assertNull(FinanceReceiptImportDateParser.tryParse("2026.05.25"));
    }

    @Test
    void isBlankShouldMatchEmptyInputs() {
        assertTrue(FinanceReceiptImportDateParser.isBlank(null));
        assertTrue(FinanceReceiptImportDateParser.isBlank(""));
        assertTrue(FinanceReceiptImportDateParser.isBlank("  "));
        assertFalse(FinanceReceiptImportDateParser.isBlank("2026-05-25"));
    }
}
