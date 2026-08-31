package cn.iocoder.yudao.framework.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * U1：权限门 403 文案（R1 / R6 / R7）。
 */
class PermissionDeniedMessageFormatterTest {

    @Test
    void namedPermission_usesChineseNameAndIdentifier() {
        String msg = PermissionDeniedMessageFormatter.format(
                List.of("finance:payment-application:create"),
                code -> "付款申请-发起");
        assertEquals("缺少权限「付款申请-发起」（finance:payment-application:create）", msg);
    }

    @Test
    void twoMissingCodes_listsEachWithoutInventedCombinedPermission() {
        String msg = PermissionDeniedMessageFormatter.format(
                List.of("finance:payment-application:query", "finance:payment-application:create"),
                code -> code.endsWith("query") ? "付款申请-查询" : "付款申请-发起");
        assertEquals(
                "缺少权限「付款申请-查询」（finance:payment-application:query）、缺少权限「付款申请-发起」（finance:payment-application:create）",
                msg);
    }

    @Test
    void blankName_omitsEmptyQuotes() {
        String msg = PermissionDeniedMessageFormatter.format(
                List.of("finance:payment-application:create"),
                code -> "  ");
        assertEquals("缺少权限（finance:payment-application:create）", msg);
    }

    @Test
    void labelLookupThrows_fallsBackToIdentifier() {
        String msg = PermissionDeniedMessageFormatter.format(
                List.of("finance:payment-application:create"),
                code -> {
                    throw new IllegalStateException("menu down");
                });
        assertEquals("缺少权限（finance:payment-application:create）", msg);
    }

    @Test
    void noCodes_returnsNullSoCallerKeepsGenericForbidden() {
        assertNull(PermissionDeniedMessageFormatter.format(List.of(), code -> "x"));
        assertNull(PermissionDeniedMessageFormatter.format(null, code -> "x"));
    }
}
