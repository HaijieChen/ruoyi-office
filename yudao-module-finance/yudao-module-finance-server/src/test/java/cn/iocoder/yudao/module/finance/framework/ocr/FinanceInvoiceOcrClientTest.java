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
        assertEquals("26312000005459012026",
                FinanceInvoiceOcrClient.parseInvoiceNo(
                        "发票号码：\n开票日期：\n名称：\n洪振业\n26312000005459012026\n2026年08月28日\n"
                                + "上海岚崖网络科技有限公司 上海西郊宾馆有限公司\n91310112MAEXHX3F7D 91310000132203459C"));
        assertNull(FinanceInvoiceOcrClient.parseInvoiceNo("无号码"));
    }

    @Test
    void parseItinerarySerialNotETicketNo() {
        String raw = "航空运输电子客票行程单 SERIAL NUMBER: 6316372299 4 "
                + "电子客票号码 07424977183958 合计 CNY 13336.00";
        assertEquals("63163722994", FinanceInvoiceOcrClient.parseInvoiceNo(raw));
        assertNull(FinanceInvoiceOcrClient.parseInvoiceNo("电子客票号码 07424977183958"));
    }

    @Test
    void parseAmountFromTotal() {
        assertEquals(new java.math.BigDecimal("1234.56"),
                FinanceInvoiceOcrClient.parseAmount("价税合计（大写）壹仟圆整（小写）¥1,234.56"));
        assertEquals(new java.math.BigDecimal("99.00"),
                FinanceInvoiceOcrClient.parseAmount("（小写）￥99.00"));
        // 全电发票：价税合计标签与金额不相邻，合计行未税 ¥3009.43 会先被 80 字窗口吃到
        assertEquals(new java.math.BigDecimal("3190.00"),
                FinanceInvoiceOcrClient.parseAmount(
                        "价税合计（大写） （小写）\n备\n注\n开票人：\n国家税务总局全国统一发票监制章上海市税务局\n"
                                + "¥3009.43 ¥180.57\n叁仟壹佰玖拾圆整 ¥ 3190.00"));
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
        assertEquals("明确购买方有限公司", FinanceInvoiceOcrClient.parseBuyerName(
                "名称：错误候选有限公司 统一社会信用代码：913100000000000001 "
                        + "名称：销售方有限公司 统一社会信用代码：913200000000000002 "
                        + "购买方名称：明确购买方有限公司 纳税人识别号"));
    }

    @Test
    void parseBuyerNameWithoutVerticalRoleLabelsUsesFirstTaxIdentityCompanyBlock() {
        String raw = "电子发票 增值税专用发票 发票号码：26300000000000000001 "
                + "名称：卡饭（上海）信息安全有限公司 "
                + "统一社会信用代码/纳税人识别号：913100000000000001 "
                + "项目名称 *住宿服务*住宿费 "
                + "名称：南京创梦酒店管理有限公司 "
                + "统一社会信用代码/纳税人识别号：913200000000000002";

        assertEquals("卡饭（上海）信息安全有限公司", FinanceInvoiceOcrClient.parseBuyerName(raw));
    }

    @Test
    void parseBuyerNameFromHorizontalRowMajorCompanyFields() {
        String raw = "名称：卡饭（上海）信息安全有限公司 "
                + "名称：南京创梦酒店管理有限公司 "
                + "统一社会信用代码/纳税人识别号：913100000000000001 "
                + "统一社会信用代码/纳税人识别号：913200000000000002";

        assertEquals("卡饭（上海）信息安全有限公司", FinanceInvoiceOcrClient.parseBuyerName(raw));
    }

    @Test
    void sellerOnlyTaxIdentityBlockDoesNotBecomeBuyer() {
        assertNull(FinanceInvoiceOcrClient.parseBuyerName(
                "名称：南京创梦酒店管理有限公司 "
                        + "统一社会信用代码/纳税人识别号：913200000000000002"));
    }

    @Test
    void parseTaxAndInvoiceType() {
        assertEquals(new java.math.BigDecimal("13.00"),
                FinanceInvoiceOcrClient.parseTaxAmount("税额：13.00 价税合计"));
        assertEquals("专票", FinanceInvoiceOcrClient.parseInvoiceType("增值税专用发票"));
        assertEquals("普票", FinanceInvoiceOcrClient.parseInvoiceType("增值税普通发票"));
        assertEquals("普票", FinanceInvoiceOcrClient.parseInvoiceType("电子发票（铁路电子客票） 票价 ￥77.00"));
        assertEquals("其他", FinanceInvoiceOcrClient.parseInvoiceType("航空运输电子客票行程单"));
        assertEquals("其他", FinanceInvoiceOcrClient.parseInvoiceType("收据"));
    }

    @Test
    void parseIcbcReceipt() {
        String raw = "中国工商银行 网上银行电子回单 电子回单号码：0918-3117-0241-1100 金额 ¥100.00元 交易流水号 82900515 时间戳 2026-07-01-04.40.28.695730 记账日期 2026年07月01日";
        assertEquals(new java.math.BigDecimal("100.00"), FinanceInvoiceOcrClient.parseReceiptAmount(raw));
        assertEquals("82900515", FinanceInvoiceOcrClient.parseReceiptSerialNo(raw));
        assertEquals(java.time.LocalDate.of(2026, 7, 1),
                FinanceInvoiceOcrClient.parseDate(FinanceInvoiceOcrClient.parseReceiptDate(raw)));
    }

    @Test
    void parseCmbReceipt() {
        String raw = "招商银行 入账回单 交易日期：2026年01月01日 交易流水：C0147BT000BP9GZ 交易金额(小写)： CNY100,000.00 回单编号：715B3J0479928";
        assertEquals(new java.math.BigDecimal("100000.00"), FinanceInvoiceOcrClient.parseReceiptAmount(raw));
        assertEquals("C0147BT000BP9GZ", FinanceInvoiceOcrClient.parseReceiptSerialNo(raw));
        assertEquals(java.time.LocalDate.of(2026, 1, 1),
                FinanceInvoiceOcrClient.parseDate(FinanceInvoiceOcrClient.parseReceiptDate(raw)));
    }

    @Test
    void parseCcbReceipt() {
        String raw = "中国建设银行网上银行电子回执 日期： 20260629 凭证号： 103H260626433661 账户明细编号-交易流水号： 65-3107836009VZUYRV6SY 小写金额 5,459.54";
        assertEquals(new java.math.BigDecimal("5459.54"), FinanceInvoiceOcrClient.parseReceiptAmount(raw));
        assertEquals("65-3107836009VZUYRV6SY", FinanceInvoiceOcrClient.parseReceiptSerialNo(raw));
        assertEquals(java.time.LocalDate.of(2026, 6, 29),
                FinanceInvoiceOcrClient.parseDate(FinanceInvoiceOcrClient.parseReceiptDate(raw)));
    }

    @Test
    void parseBocReceipt() {
        String raw = "中国银行 国内支付业务收款回单 日期： 2026年04月01日 金额：CNY258.66 交易流水号：111592080-478 回单编号：702692759947145";
        assertEquals(new java.math.BigDecimal("258.66"), FinanceInvoiceOcrClient.parseReceiptAmount(raw));
        assertEquals("111592080-478", FinanceInvoiceOcrClient.parseReceiptSerialNo(raw));
        assertEquals(java.time.LocalDate.of(2026, 4, 1),
                FinanceInvoiceOcrClient.parseDate(FinanceInvoiceOcrClient.parseReceiptDate(raw)));
    }

    @Test
    void parseAbcReceipt() {
        String raw = "中国农业银行 网上银行电子回单 回单编号：31317259950084332781 金额（小写） 55136.10 交易时间 2026-07-01 15:05:44 会计日期 20260701 凭证号 09350350200002992";
        assertEquals(new java.math.BigDecimal("55136.10"), FinanceInvoiceOcrClient.parseReceiptAmount(raw));
        assertEquals("31317259950084332781", FinanceInvoiceOcrClient.parseReceiptSerialNo(raw));
        assertEquals(java.time.LocalDate.of(2026, 7, 1),
                FinanceInvoiceOcrClient.parseDate(FinanceInvoiceOcrClient.parseReceiptDate(raw)));
    }

    @Test
    void parseBocomReceipt() {
        String raw = "交通银行 回单 回单编号： 260G894672A8 金额： 0.30 记账日期： 20260705 会计流水号： PEA0000U60384512";
        assertEquals(new java.math.BigDecimal("0.30"), FinanceInvoiceOcrClient.parseReceiptAmount(raw));
        assertEquals("PEA0000U60384512", FinanceInvoiceOcrClient.parseReceiptSerialNo(raw));
        assertEquals(java.time.LocalDate.of(2026, 7, 5),
                FinanceInvoiceOcrClient.parseDate(FinanceInvoiceOcrClient.parseReceiptDate(raw)));
    }

    @Test
    void parseBosReceipt() {
        String raw = "上海银行业务回单 回单编号：29420260701034827001599286 记账日期 2026-07-01 核心流水号 V026070100241911 金额（小写） 55,027.60";
        assertEquals(new java.math.BigDecimal("55027.60"), FinanceInvoiceOcrClient.parseReceiptAmount(raw));
        assertEquals("V026070100241911", FinanceInvoiceOcrClient.parseReceiptSerialNo(raw));
        assertEquals(java.time.LocalDate.of(2026, 7, 1),
                FinanceInvoiceOcrClient.parseDate(FinanceInvoiceOcrClient.parseReceiptDate(raw)));
    }

    @Test
    void parseCibReceipt() {
        String raw = "兴业银行 收款回单 回单编号：202607020100036802 交易日期：2026-07-02 07:27:12 金额（小写）：CNY50,000.00";
        assertEquals(new java.math.BigDecimal("50000.00"), FinanceInvoiceOcrClient.parseReceiptAmount(raw));
        assertEquals("202607020100036802", FinanceInvoiceOcrClient.parseReceiptSerialNo(raw));
        assertEquals(java.time.LocalDate.of(2026, 7, 2),
                FinanceInvoiceOcrClient.parseDate(FinanceInvoiceOcrClient.parseReceiptDate(raw)));
    }

    @Test
    void parseCompactYmdDate() {
        assertEquals(java.time.LocalDate.of(2026, 6, 29), FinanceInvoiceOcrClient.parseDate("20260629"));
        assertEquals(java.time.LocalDate.of(2026, 7, 5), FinanceInvoiceOcrClient.parseDate("20260705"));
    }
}
