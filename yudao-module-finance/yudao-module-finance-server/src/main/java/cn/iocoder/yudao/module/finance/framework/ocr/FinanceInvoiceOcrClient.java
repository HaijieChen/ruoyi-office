package cn.iocoder.yudao.module.finance.framework.ocr;

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
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return Result.empty();
        }
        if (StrUtil.isBlank(invoiceFileUrl)) {
            return Result.empty();
        }
        try {
            byte[] bytes = download(invoiceFileUrl.trim());
            String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/ocr/invoice";
            JSONObject payload = new JSONObject();
            if (bytes != null && bytes.length > 0) {
                payload.set("fileBase64", java.util.Base64.getEncoder().encodeToString(bytes));
            } else {
                String abs = invoiceFileUrl.trim();
                if (abs.startsWith("/")) {
                    abs = "http://127.0.0.1:48080" + abs;
                }
                payload.set("fileUrl", abs);
            }
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
            return new Result(parseDate(json.getStr("feeDate")), json.getBigDecimal("amount"), json.getStr("rawText"));
        } catch (Exception ex) {
            log.warn("[ocr] failed url={}: {}", invoiceFileUrl, ex.toString());
            return Result.empty();
        }
    }

    private byte[] download(String url) {
        String abs = url;
        if (abs.startsWith("/")) {
            abs = "http://127.0.0.1:48080" + abs;
        }
        HttpResponse resp = HttpRequest.get(abs).timeout(10000).execute();
        if (!resp.isOk()) {
            return null;
        }
        return resp.bodyBytes();
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

    public record Result(LocalDate feeDate, BigDecimal amount, String rawText) {
        static Result empty() {
            return new Result(null, null, null);
        }
    }
}
