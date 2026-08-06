package cn.iocoder.yudao.module.finance.controller.admin.payment;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PAY-R16：replay endpoint 权限注解契约 — 仅专用 replay-outcome，不含 update。
 */
class FinancePaymentReplayPermissionContractTest {

    @Test
    void replayOutcomePreAuthorizeIsReplayOnly() throws Exception {
        Method m = FinancePaymentApplicationController.class.getMethod(
                "replayOutcome", Long.class, String.class, String.class);
        PreAuthorize pa = m.getAnnotation(PreAuthorize.class);
        assertNotNull(pa, "missing @PreAuthorize on replayOutcome");
        String expr = pa.value();
        assertTrue(expr.contains("finance:payment-application:replay-outcome"), expr);
        assertFalse(expr.contains("finance:payment-application:update"),
                "update must not imply replay: " + expr);
        assertFalse(expr.toLowerCase().contains(" or "),
                "must not OR other permissions: " + expr);
    }
}
