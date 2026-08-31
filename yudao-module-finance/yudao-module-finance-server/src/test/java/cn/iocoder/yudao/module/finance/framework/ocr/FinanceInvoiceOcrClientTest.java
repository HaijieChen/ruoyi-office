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
        assertEquals("25317000000178817093",
                FinanceInvoiceOcrClient.parseInvoiceNo("发票号码 No. 25317000000178817093"));
        assertNull(FinanceInvoiceOcrClient.parseInvoiceNo("无号码"));
    }

    @Test
    void parseAmountFromTotal() {
        assertEquals(new java.math.BigDecimal("1234.56"),
                FinanceInvoiceOcrClient.parseAmount("价税合计（大写）壹仟圆整（小写）¥1,234.56"));
        assertEquals(new java.math.BigDecimal("99.00"),
                FinanceInvoiceOcrClient.parseAmount("（小写）￥99.00"));
        assertNull(FinanceInvoiceOcrClient.parseAmount("无金额"));
    }

    @Test
    void parseBuyerName() {
        assertEquals("上海文枢科技有限公司",
                FinanceInvoiceOcrClient.parseBuyerName("购买方名称：上海文枢科技有限公司 纳税人识别号"));
        assertEquals("上海文枢科技有限公司",
                FinanceInvoiceOcrClient.parseBuyerName("购买方信息\n名称：上海文枢科技有限公司\n统一社会信用代码"));
        assertEquals("北京某某科技有限公司",
                FinanceInvoiceOcrClient.parseBuyerName("购货单位：北京某某科技有限公司 纳税人识别号"));
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
