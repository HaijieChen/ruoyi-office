package cn.iocoder.yudao.module.finance.framework.ocr;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class FinanceInvoiceOcrClient {

    private final FinanceInvoiceOcrProperties properties;

    public FinanceInvoiceOcrClient(FinanceInvoiceOcrProperties properties) {
        this.properties = properties;
    }

    public Result recognize(String invoiceFileUrl) {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return Result.empty();
        }
        try {
            HttpResponse resp = HttpRequest.post(properties.getBaseUrl().replaceAll("/+$", "") + "/ocr/invoice")
                    .timeout(properties.getTimeoutMs())
                    .body(JSONUtil.toJsonStr(new JSONObject().set("fileUrl", invoiceFileUrl)))
                    .contentType("application/json")
                    .execute();
            if (!resp.isOk()) {
                return Result.empty();
            }
            JSONObject json = JSONUtil.parseObj(resp.body());
            String date = json.getStr("feeDate");
            BigDecimal amount = json.getBigDecimal("amount");
            return new Result(date == null || date.isBlank() ? null : LocalDate.parse(date.substring(0, 10)),
                    amount, json.getStr("rawText"));
        } catch (Exception ignored) {
            return Result.empty();
        }
    }

    public record Result(LocalDate feeDate, BigDecimal amount, String rawText) {
        static Result empty() {
            return new Result(null, null, null);
        }
    }
}
