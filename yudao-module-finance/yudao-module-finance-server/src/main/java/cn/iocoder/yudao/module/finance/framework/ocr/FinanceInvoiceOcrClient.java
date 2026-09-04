package cn.iocoder.yudao.module.finance.framework.ocr;

import com.fasterxml.jackson.annotation.JsonFormat;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

@Component
public class FinanceInvoiceOcrClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceInvoiceOcrClient.class);

    private final FinanceInvoiceOcrProperties properties;

    public FinanceInvoiceOcrClient(FinanceInvoiceOcrProperties properties) {
        this.properties = properties;
    }

    public Result recognize(String invoiceFileUrl) {
        if (StrUtil.isBlank(invoiceFileUrl)
                || properties.getBaseUrl() == null
                || properties.getBaseUrl().isBlank()) {
            return Result.empty();
        }
        return recognizeBytes(download(invoiceFileUrl.trim()));
    }

    public Result recognizeBytes(byte[] bytes) {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return Result.empty();
        }
        if (bytes == null || bytes.length == 0) {
            return Result.empty();
        }
        try {
            String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/ocr/invoice";
            JSONObject payload = new JSONObject();
            payload.set("fileBase64", java.util.Base64.getEncoder().encodeToString(bytes));
            HttpResponse resp = HttpRequest.post(endpoint)
                    .timeout(Math.max(properties.getTimeoutMs(), 15000))
                    .body(JSONUtil.toJsonStr(payload))
                    .contentType("application/json")
                    .execute();
            if (!resp.isOk()) {
                log.warn("[ocr] sidecar http={} body={}", resp.getStatus(), StrUtil.sub(resp.body(), 0, 200));
                return Result.empty();
            }
            JSONObject json = JSONUtil.parseObj(resp.body());
            String invoiceNo = json.getStr("invoiceNo");
            String rawText = json.getStr("rawText");
            if (StrUtil.isBlank(invoiceNo)) {
                invoiceNo = parseInvoiceNo(rawText);
            }
            if (StrUtil.isBlank(invoiceNo)) {
                invoiceNo = parseReceiptSerialNo(rawText);
            }
            BigDecimal amount = json.getBigDecimal("amount");
            if (amount == null) {
                amount = parseAmount(rawText);
            }
            if (amount == null) {
                amount = parseReceiptAmount(rawText);
            }
            BigDecimal taxAmount = json.getBigDecimal("taxAmount");
            if (taxAmount == null) {
                taxAmount = parseTaxAmount(rawText);
            }
            String invoiceType = json.getStr("invoiceType");
            if (StrUtil.isBlank(invoiceType)) {
                invoiceType = parseInvoiceType(rawText);
            }
            String buyerName = json.getStr("buyerName");
            if (StrUtil.isBlank(buyerName)) {
                buyerName = parseBuyerName(rawText);
            }
            LocalDate feeDate = parseDate(json.getStr("feeDate"));
            if (feeDate == null) {
                feeDate = parseDate(parseFeeDate(rawText));
            }
            if (feeDate == null) {
                feeDate = parseDate(parseReceiptDate(rawText));
            }
            return new Result(feeDate, amount, rawText, invoiceNo, taxAmount, invoiceType, buyerName, false);
        } catch (Exception ex) {
            log.warn("[ocr] failed: {}", ex.toString());
            return Result.empty();
        }
    }

    private byte[] download(String url) {
        String abs = encodeUrl(url.startsWith("/") ? "http://127.0.0.1:48080" + url : url);
        HttpResponse resp = HttpRequest.get(abs).timeout(15000).execute();
        if (!resp.isOk()) {
            log.warn("[ocr] download http={} url={}", resp.getStatus(), abs);
            return null;
        }
        return resp.bodyBytes();
    }

    static String encodeUrl(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            String[] segs = u.getPath().split("/", -1);
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < segs.length; i++) {
                if (i > 0) {
                    path.append('/');
                }
                if (!segs[i].isEmpty()) {
                    path.append(java.net.URLEncoder.encode(segs[i], java.nio.charset.StandardCharsets.UTF_8)
                            .replace("+", "%20"));
                }
            }
            int port = u.getPort();
            String host = port > 0 ? u.getHost() + ":" + port : u.getHost();
            String q = u.getQuery() == null ? "" : "?" + u.getQuery();
            return u.getProtocol() + "://" + host + path + q;
        } catch (Exception e) {
            return url;
        }
    }

    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.trim().replace("年", "-").replace("月", "-").replace("日", "")
                .replace(".", "-").replace("/", "-");
        if (n.matches("20\\d{6}")) {
            n = n.substring(0, 4) + "-" + n.substring(4, 6) + "-" + n.substring(6, 8);
        }
        if (n.length() >= 10) {
            n = n.substring(0, 10);
        }
        try {
            DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.YEAR, 4)
                    .appendLiteral('-')
                    .appendValue(ChronoField.MONTH_OF_YEAR)
                    .appendLiteral('-')
                    .appendValue(ChronoField.DAY_OF_MONTH)
                    .toFormatter();
            return LocalDate.parse(n, fmt);
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(n);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    static String parseInvoiceNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        java.util.regex.Matcher serial = java.util.regex.Pattern
                .compile("(?:印刷序号|SERIAL\\s*NUM(?:BER)?)\\s*[:：]?\\s*([0-9][0-9 ]{6,24}\\d)",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(raw);
        if (serial.find()) {
            return serial.group(1).replaceAll("\\s+", "");
        }
        java.util.regex.Matcher spaced = java.util.regex.Pattern
                .compile("(?:印刷序号|SERIAL\\s*NUM(?:BER)?).{0,80}?((?:\\d[ \\t]*){10,14})",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(raw);
        if (spaced.find()) {
            String digits = spaced.group(1).replaceAll("\\s+", "");
            if (digits.length() >= 10 && digits.length() <= 14) {
                return digits;
            }
        }
        String compact = raw.replaceAll("[\\s　]", "");
        java.util.regex.Matcher labeled = java.util.regex.Pattern
                .compile("发票号码[:：]?[^0-9]{0,8}([0-9]{8,20})")
                .matcher(compact);
        if (labeled.find()) {
            return labeled.group(1);
        }
        java.util.regex.Matcher eInvoice = java.util.regex.Pattern
                .compile("(?<!\\d)(\\d{20})(?!\\d)")
                .matcher(raw);
        if (eInvoice.find()) {
            return eInvoice.group(1);
        }
        java.util.regex.Matcher number = java.util.regex.Pattern
                .compile("(?<!电子客票)号码[:：]([0-9]{8,20})")
                .matcher(compact);
        if (number.find()) {
            return number.group(1);
        }
        return null;
    }

    static String parseFeeDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:开票日期|日期)[:：]?\\s*(20\\d{2}[-./年]\\d{1,2}[-./月]\\d{1,2})")
                .matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        m = java.util.regex.Pattern.compile("(20\\d{2}[-./年]\\d{1,2}[-./月]\\d{1,2})").matcher(raw);
        return m.find() ? m.group(1) : null;
    }

    static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replaceAll("[\\s　]", "");
        String[] patterns = {
                "价税合计(?:\\(大写\\)|（大写）)?[^0-9¥￥]{0,80}(?:\\(小写\\)|（小写）)?[¥￥]?((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
                "(?:\\(小写\\)|（小写）)[¥￥]?((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
                "[¥￥]((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
        };
        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(compact);
            String last = null;
            while (m.find()) {
                last = m.group(1);
            }
            if (last != null) {
                return new BigDecimal(last.replace(",", ""));
            }
        }
        return null;
    }

    static String parseBuyerName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replaceAll("[\\s　]+", "");
        String[] patterns = {
                "购买方(?:信息)?名称[:：]?([^销税]{2,80}?)(?:纳税人识别号|统一社会信用代码|销售方|$)",
                "购货单位(?:名称)?[:：]?([^销税]{2,80}?)(?:纳税人识别号|统一社会信用代码|销售方|$)",
                "购买方[^销]{0,40}名称[:：]?([^销税]{2,80}?)(?:纳税人识别号|统一社会信用代码|销售方|$)",
        };
        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(compact);
            if (m.find()) {
                String name = cleanBuyerName(m.group(1));
                if (name != null) {
                    return name;
                }
            }
        }
        java.util.regex.Matcher companyNameField = java.util.regex.Pattern
                .compile("(?<!项目)名称[:：]?(.{2,80}?)(?=(?<!项目)名称[:：]?|纳税人识别号|统一社会信用代码)")
                .matcher(compact);
        String firstCompanyName = null;
        int companyCount = 0;
        while (companyNameField.find()) {
            String name = cleanBuyerName(companyNameField.group(1));
            if (name == null) {
                continue;
            }
            if (firstCompanyName == null) {
                firstCompanyName = name;
            }
            companyCount++;
        }
        int taxIdentityLabelCount = 0;
        java.util.regex.Matcher taxIdentityLabel = java.util.regex.Pattern
                .compile("纳税人识别号|统一社会信用代码")
                .matcher(compact);
        while (taxIdentityLabel.find()) {
            taxIdentityLabelCount++;
        }
        // 横版 OCR 可能按区块或按行输出；仅在买卖双方名称和税号标签都齐全时取首个名称。
        if (companyCount >= 2 && taxIdentityLabelCount >= 2) {
            return firstCompanyName;
        }
        return null;
    }

    static String cleanBuyerName(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.trim()
                .replaceAll("[，,。；;].*$", "")
                .replaceAll("(纳税人识别号|统一社会信用代码|销售方).*$", "")
                .trim();
        return n.isEmpty() ? null : n;
    }

    static BigDecimal parseTaxAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("税额[:：]?\\s*[¥￥]?\\s*(\\d+\\.\\d{2})").matcher(raw);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last == null ? null : new BigDecimal(last);
    }

    /** 银行电子回单金额：小写/CNY/¥ */
    static BigDecimal parseReceiptAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replaceAll("[\\s　]", "");
        String[] patterns = {
                "交易金额(?:\\(小写\\)|（小写）)[:：]?(?:CNY)?((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
                "金额(?:\\(小写\\)|（小写）)[:：]?(?:CNY)?((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
                "小写金额[:：]?((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
                "金额[:：]?[¥￥]?(?:CNY)?((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
                "CNY((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
                "[¥￥]((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})",
        };
        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(compact);
            if (m.find()) {
                return new BigDecimal(m.group(1).replace(",", ""));
            }
        }
        return null;
    }

    /** 银行电子回单流水号 */
    static String parseReceiptSerialNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replaceAll("[\\s　]", "");
        String[] patterns = {
                "交易流水号[:：]?([A-Za-z0-9-]{6,40})",
                "交易流水[:：]?([A-Za-z0-9-]{6,40})",
                "账户明细编号-交易流水号[:：]?([A-Za-z0-9-]{6,40})",
                "核心流水号[:：]?([A-Za-z0-9-]{6,40})",
                "会计流水号[:：]?([A-Za-z0-9-]{6,40})",
                "电子回单号码[:：]?([A-Za-z0-9-]{6,40})",
                "回单编号[:：]?([A-Za-z0-9-]{6,40})",
                "凭证号[:：]?([A-Za-z0-9-]{6,40})",
        };
        for (String pattern : patterns) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(compact);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    /** 银行电子回单日期 */
    static String parseReceiptDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?:记账日期|交易日期|交易时间|会计日期|时间戳|日期)[:：]?\\s*(20\\d{2}[-./年]\\d{1,2}[-./月]\\d{1,2}|20\\d{6})")
                .matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        String compact = raw.replaceAll("[\\s　]", "");
        m = java.util.regex.Pattern.compile(
                "(?:记账日期|交易日期|交易时间|会计日期|时间戳|日期)[:：]?(20\\d{2}[-./年]\\d{1,2}[-./月]\\d{1,2}|20\\d{6})")
                .matcher(compact);
        return m.find() ? m.group(1) : null;
    }

    static String parseInvoiceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "其他";
        }
        if (raw.contains("航空运输电子客票行程单")
                || raw.toUpperCase().contains("ITINERARY/RECEIPT OF E-TICKET")) {
            return "其他";
        }
        if (raw.contains("电子发票（铁路电子客票）") || raw.contains("铁路电子客票")) {
            return "普票";
        }
        if (raw.contains("增值税专用发票") || raw.contains("专用发票")
                || (raw.contains("专票") && !raw.contains("普票"))) {
            return "专票";
        }
        if (raw.contains("增值税普通发票") || raw.contains("普通发票")
                || raw.contains("电子发票（普通发票）") || raw.contains("普票")) {
            return "普票";
        }
        return "其他";
    }

    public record Result(@JsonFormat(pattern = "yyyy-MM-dd") LocalDate feeDate, BigDecimal amount, String rawText,
                         String invoiceNo, BigDecimal taxAmount, String invoiceType, String buyerName, boolean used) {
        static Result empty() {
            return new Result(null, null, null, null, null, "其他", null, false);
        }

        public Result withUsed(boolean usedFlag) {
            return new Result(feeDate, amount, rawText, invoiceNo, taxAmount, invoiceType, buyerName, usedFlag);
        }
    }
}
