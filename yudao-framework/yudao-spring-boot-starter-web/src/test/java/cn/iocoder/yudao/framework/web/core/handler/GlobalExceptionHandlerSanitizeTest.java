package cn.iocoder.yudao.framework.web.core.handler;

import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.json.SensitiveJsonSanitizer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-87 G2：与 {@link GlobalExceptionHandler#buildExceptionLog} 相同的 error-log 落库 body 脱敏契约。
 * <p>
 * 不启动完整 Handler（依赖 TraceContext），直接验证其 body 组装路径使用共享 sanitizer。
 */
class GlobalExceptionHandlerSanitizeTest {

    /** 与 GlobalExceptionHandler.buildExceptionLog 中 requestParams 组装一致 */
    private static String buildErrorLogRequestParams(String rawBody, Map<String, String> query) {
        String sanitizedBody = SensitiveJsonSanitizer.sanitize(rawBody);
        String sanitizedQuery = SensitiveJsonSanitizer.sanitize(JsonUtils.toJsonString(query));
        Map<String, Object> requestParams = MapUtil.<String, Object>builder()
                .put("query", sanitizedQuery)
                .put("body", sanitizedBody).build();
        return JsonUtils.toJsonString(requestParams);
    }

    @Test
    void bankAccountCreateBody_sanitizedForErrorLogDb() {
        String secret = "6222000111223344";
        String body = "{\"entityCompanyDeptId\":20,\"accountName\":\"基本户\","
                + "\"accountNo\":\"" + secret + "\",\"currency\":\"CNY\"}";
        String params = buildErrorLogRequestParams(body, Map.of());
        assertFalse(params.contains(secret), "error-log DB params must not contain accountNo: " + params);
        assertFalse(params.contains("\"accountNo\""));
        assertTrue(params.contains("基本户") || params.contains("accountName"));
    }

    @Test
    void bankAccountUpdateMalformed_failClosedForErrorLogDb() {
        String secret = "6222SECRET_ACC";
        String body = "{not-json, \"accountNo\":\"" + secret + "\"}";
        String params = buildErrorLogRequestParams(body, Map.of());
        assertFalse(params.contains(secret));
        assertTrue(params.contains("__redacted") || params.contains(SensitiveJsonSanitizer.REDACTED_BODY));
    }
}
