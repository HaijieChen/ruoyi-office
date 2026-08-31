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
            BigDecimal taxAmount = json.getBigDecimal("taxAmount");
            if (taxAmount == null) {
                taxAmount = parseTaxAmount(rawText);
            }
            String invoiceType = json.getStr("invoiceType");
            if (StrUtil.isBlank(invoiceType)) {
                invoiceType = parseInvoiceType(rawText);
            }
            return new Result(parseDate(json.getStr("feeDate")), json.getBigDecimal("amount"),
                    rawText, invoiceNo, taxAmount, invoiceType, false);
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
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("发票号码[:：]?\\s*([0-9]{8,20})").matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        return null;
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

    static String parseInvoiceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "其他";
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
                         String invoiceNo, BigDecimal taxAmount, String invoiceType, boolean used) {
        static Result empty() {
            return new Result(null, null, null, null, null, "其他", false);
        }

        public Result withUsed(boolean usedFlag) {
            return new Result(feeDate, amount, rawText, invoiceNo, taxAmount, invoiceType, usedFlag);
        }
    }
}
