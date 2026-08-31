package cn.iocoder.yudao.framework.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailedPermissionHolderTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordThenConsume_returnsCodesAndSecondConsumeIsEmpty() {
        bindRequest();
        FailedPermissionHolder.record("finance:payment-application:create");
        assertEquals(List.of("finance:payment-application:create"), FailedPermissionHolder.consume());
        assertTrue(FailedPermissionHolder.consume().isEmpty());
    }

    @Test
    void laterRecord_overwritesEarlierCodes() {
        bindRequest();
        FailedPermissionHolder.record("finance:payment-application:query");
        FailedPermissionHolder.record("finance:payment-application:update");
        assertEquals(List.of("finance:payment-application:update"), FailedPermissionHolder.consume());
    }

    @Test
    void clear_dropsRecordedCodes() {
        bindRequest();
        FailedPermissionHolder.record("finance:payment-application:query");
        FailedPermissionHolder.clear();
        assertTrue(FailedPermissionHolder.consume().isEmpty());
    }

    @Test
    void formatForCurrentRequest_afterRecord_yieldsR1AndClears() {
        bindRequest();
        FailedPermissionHolder.record("finance:payment-application:create");
        String msg = PermissionDeniedMessageFormatter.formatForCurrentRequest();
        assertEquals("缺少权限（finance:payment-application:create）", msg);
        assertTrue(FailedPermissionHolder.consume().isEmpty());
    }

    private static void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }
}
