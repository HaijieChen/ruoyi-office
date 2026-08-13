package cn.iocoder.yudao.framework.apilog.core.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-87 F3/G2：脱敏 fail-closed（合法 JSON 脱敏；畸形 JSON 不回传原文；
 * access-log 落库与 error log 均无 accountNo 原文）。
 */
class ApiAccessLogSanitizeTest {

    @Test
    void legalJsonRemovesAccountNo() throws Exception {
        Method m = ApiAccessLogFilter.class.getDeclaredMethod(
                "sanitizeJson", String.class, String[].class);
        m.setAccessible(true);
        // 真实公司银行账户 create body
        String input = "{\"entityCompanyDeptId\":20,\"accountName\":\"基本户\","
                + "\"accountNo\":\"6222000111223344\",\"currency\":\"CNY\"}";
        String out = (String) m.invoke(null, input, new String[]{});
        assertNotNull(out);
        assertFalse(out.contains("6222000111223344"));
        assertFalse(out.contains("accountNo"));
        assertTrue(out.contains("基本户") || out.contains("accountName"));
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

    @Test
    void malformedJsonErrorLogsDoNotContainAccountNo() throws Exception {
        Logger filterLogger = (Logger) LoggerFactory.getLogger(ApiAccessLogFilter.class);
        Logger jsonLogger = (Logger) LoggerFactory.getLogger(JsonUtils.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        jsonLogger.addAppender(appender);
        filterLogger.setLevel(Level.ERROR);
        jsonLogger.setLevel(Level.ERROR);
        try {
            Method m = ApiAccessLogFilter.class.getDeclaredMethod(
                    "sanitizeJson", String.class, String[].class);
            m.setAccessible(true);
            String secret = "6222SECRET_ACCOUNT";
            String input = "{not-json, \"accountNo\":\"" + secret + "\"}";
            String out = (String) m.invoke(null, input, new String[]{});
            assertEquals(ApiAccessLogFilter.REDACTED_BODY, out);
            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertFalse(logs.contains(secret), "error log must not contain accountNo: " + logs);
            assertFalse(logs.contains("6222SECRET"), "error log must not contain partial account: " + logs);
        } finally {
            filterLogger.detachAppender(appender);
            jsonLogger.detachAppender(appender);
            appender.stop();
        }
    }
}
