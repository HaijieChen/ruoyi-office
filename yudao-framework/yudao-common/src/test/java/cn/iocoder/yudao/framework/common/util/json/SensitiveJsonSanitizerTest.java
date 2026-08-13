package cn.iocoder.yudao.framework.common.util.json;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-87 G2：共享 sanitizer — 合法脱敏 + 畸形 fail-closed + 日志无原文。
 */
class SensitiveJsonSanitizerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(SensitiveJsonSanitizer.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ERROR);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void bankAccountCreateBody_removesAccountNo() {
        // 真实公司银行账户 create/update body 形态
        String body = "{\"entityCompanyDeptId\":20,\"accountName\":\"基本户\","
                + "\"bankName\":\"工行\",\"accountHolder\":\"甲公司\","
                + "\"accountNo\":\"6222000111223344\",\"currency\":\"CNY\",\"status\":0}";
        String out = SensitiveJsonSanitizer.sanitize(body);
        assertNotNull(out);
        assertFalse(out.contains("6222000111223344"));
        assertFalse(out.contains("accountNo"));
        assertTrue(out.contains("基本户") || out.contains("accountName"));
    }

    @Test
    void malformedBody_failClosed_noLiteralInReturnOrLog() {
        String secret = "6222SECRET_BANK";
        String bad = "{not-json, \"accountNo\":\"" + secret + "\"}";
        String out = SensitiveJsonSanitizer.sanitize(bad);
        assertEquals(SensitiveJsonSanitizer.REDACTED_BODY, out);
        assertFalse(out.contains(secret));
        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(logs.contains(secret), "error log must not contain accountNo: " + logs);
    }

    @Test
    void sanitizeOrRedact_nonJsonWithAccountHint_redacts() {
        String raw = "accountNo=6222000999888777 plain";
        String out = SensitiveJsonSanitizer.sanitizeOrRedact(raw);
        assertEquals(SensitiveJsonSanitizer.REDACTED_BODY, out);
        assertFalse(out.contains("6222000999888777"));
    }
}
