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
 * EXP-87 G2：JsonUtils 解析失败不得将 body 原文写入 error log。
 */
class JsonUtilsParseLogTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(JsonUtils.class);
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
    void parseTreeMalformedDoesNotLogAccountNoLiteral() {
        String secret = "622200011122SECRET";
        String malformed = "{not-json, \"accountNo\":\"" + secret + "\"}";
        assertThrows(RuntimeException.class, () -> JsonUtils.parseTree(malformed));

        String all = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(all.contains(secret), "error log must not contain accountNo literal: " + all);
        assertFalse(all.contains("accountNo"), "error log must not contain accountNo key with body: " + all);
        assertTrue(all.contains("length=") || all.toLowerCase().contains("length"),
                "expected length-only log, got: " + all);
    }

    @Test
    void parseObjectMalformedDoesNotLogBody() {
        String secret = "BANK-ACCOUNT-998877";
        assertThrows(RuntimeException.class,
                () -> JsonUtils.parseObject("{bad json " + secret + "}", Object.class));
        String all = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(all.contains(secret), "error log must not contain original body: " + all);
    }
}
