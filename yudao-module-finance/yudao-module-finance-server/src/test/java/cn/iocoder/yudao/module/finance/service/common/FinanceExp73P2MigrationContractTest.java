package cn.iocoder.yudao.module.finance.service.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** EXP-73 P2：迁移脚本与币种契约门禁 */
class FinanceExp73P2MigrationContractTest {

    @Test
    void p2MigrationSqlMustExist() throws Exception {
        Path sql = findRoot().resolve("sql/mysql/finance_exp73_p2_entity_currency.sql");
        assertTrue(Files.exists(sql));
        String text = Files.readString(sql);
        assertTrue(text.contains("finance_contract_application"));
        assertTrue(text.contains("entity_company_dept_id"));
        assertTrue(text.contains("finance_business_order"));
        assertTrue(text.contains("finance_invoice_application"));
        assertTrue(text.contains("finance_bank_receipt"));
        assertTrue(text.contains("currency"));
    }

    @Test
    void currencySupportWhitelist() {
        assertEquals("CNY", FinanceCurrencySupport.requireSupported("cny"));
        assertEquals("USD", FinanceCurrencySupport.requireSupported("USD"));
        assertNull(FinanceCurrencySupport.normalizeOptional(null));
        assertThrows(Exception.class, () -> FinanceCurrencySupport.requireSupported("EUR"));
        assertDoesNotThrow(() -> FinanceCurrencySupport.assertSameIfBothPresent("CNY", "CNY"));
        assertDoesNotThrow(() -> FinanceCurrencySupport.assertSameIfBothPresent(null, "CNY"));
        assertThrows(Exception.class, () -> FinanceCurrencySupport.assertSameIfBothPresent("CNY", "USD"));
    }

    private static Path findRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("sql/mysql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("repo root not found");
        }
        return current;
    }
}
