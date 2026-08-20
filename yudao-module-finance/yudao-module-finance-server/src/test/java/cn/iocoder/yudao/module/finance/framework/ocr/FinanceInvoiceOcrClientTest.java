package cn.iocoder.yudao.module.finance.framework.ocr;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FinanceInvoiceOcrClientTest {

    @Test
    void blankBaseUrlReturnsEmpty() {
        FinanceInvoiceOcrProperties props = new FinanceInvoiceOcrProperties();
        props.setBaseUrl("");
        FinanceInvoiceOcrClient client = new FinanceInvoiceOcrClient(props);
        FinanceInvoiceOcrClient.Result result = client.recognize("https://x/a.jpg");
        assertNull(result.feeDate());
        assertNull(result.amount());
    }

    @Test
    void parseChineseAndSlashDates() {
        assertEquals(LocalDate.of(2026, 8, 19), FinanceInvoiceOcrClient.parseDate("2026年08月19日"));
        assertEquals(LocalDate.of(2026, 8, 1), FinanceInvoiceOcrClient.parseDate("2026/8/1"));
        assertNull(FinanceInvoiceOcrClient.parseDate(""));
    }

    @Test
    void parseInvoiceNo() {
        assertEquals("26317000002934677164",
                FinanceInvoiceOcrClient.parseInvoiceNo("电子发票 发票号码：26317000002934677164 开票日期"));
        assertNull(FinanceInvoiceOcrClient.parseInvoiceNo("无号码"));
    }
}
