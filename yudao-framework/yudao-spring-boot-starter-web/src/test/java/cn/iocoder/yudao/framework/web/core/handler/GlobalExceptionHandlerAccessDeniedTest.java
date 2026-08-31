package cn.iocoder.yudao.framework.web.core.handler;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.security.FailedPermissionHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerAccessDeniedTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler("test", mock(ApiErrorLogCommonApi.class));

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void rememberedCode_returnsR1MsgWithBiz403() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/finance/payment-application/create");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        FailedPermissionHolder.record("finance:payment-application:create");

        CommonResult<?> result = handler.accessDeniedExceptionHandler(req, new AccessDeniedException("denied"));
        assertEquals(FORBIDDEN.getCode(), result.getCode());
        assertEquals("缺少权限（finance:payment-application:create）", result.getMsg());
    }

    @Test
    void noRememberedCode_keepsGenericForbidden() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/finance/payment-application/create");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        CommonResult<?> result = handler.accessDeniedExceptionHandler(req, new AccessDeniedException("denied"));
        assertEquals(FORBIDDEN.getCode(), result.getCode());
        assertEquals(FORBIDDEN.getMsg(), result.getMsg());
    }
}
