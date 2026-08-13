package cn.iocoder.yudao.framework.apilog.core.filter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-87 F3：脱敏 fail-closed 行为（合法 JSON 脱敏；畸形 JSON 不回传原文）。
 */
class ApiAccessLogSanitizeTest {

    @Test
    void legalJsonRemovesAccountNo() throws Exception {
        Method m = ApiAccessLogFilter.class.getDeclaredMethod(
                "sanitizeJson", String.class, String[].class);
        m.setAccessible(true);
        String input = "{\"accountNo\":\"622200011122\",\"name\":\"ok\"}";
        String out = (String) m.invoke(null, input, new String[]{});
        assertNotNull(out);
        assertFalse(out.contains("622200011122"));
        assertTrue(out.contains("ok") || out.contains("name"));
    }

    @Test
    void malformedJsonIsFailClosedNotOriginal() throws Exception {
        Method m = ApiAccessLogFilter.class.getDeclaredMethod(
                "sanitizeJson", String.class, String[].class);
        m.setAccessible(true);
        String input = "{not-json, \"accountNo\":\"6222SECRET\"}";
        String out = (String) m.invoke(null, input, new String[]{});
        assertEquals(ApiAccessLogFilter.REDACTED_BODY, out);
        assertFalse(out.contains("6222SECRET"));
        assertFalse(out.contains("not-json"));
    }
}
