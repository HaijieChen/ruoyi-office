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
        assertEquals("25317000000178817093",
                FinanceInvoiceOcrClient.parseInvoiceNo("发票号码\n25317000000178817093 开票日期"));
        assertNull(FinanceInvoiceOcrClient.parseInvoiceNo("无号码"));
    }

    @Test
    void parseBuyerName() {
        assertEquals("上海文枢科技有限公司",
                FinanceInvoiceOcrClient.parseBuyerName("购买方名称：上海文枢科技有限公司 纳税人识别号"));
        assertNull(FinanceInvoiceOcrClient.parseBuyerName("销售方名称：某商户"));
    }

    @Test
    void parseTaxAndInvoiceType() {
        assertEquals(new java.math.BigDecimal("13.00"),
                FinanceInvoiceOcrClient.parseTaxAmount("税额：13.00 价税合计"));
        assertEquals("专票", FinanceInvoiceOcrClient.parseInvoiceType("增值税专用发票"));
        assertEquals("普票", FinanceInvoiceOcrClient.parseInvoiceType("增值税普通发票"));
        assertEquals("其他", FinanceInvoiceOcrClient.parseInvoiceType("收据"));
    }
}
