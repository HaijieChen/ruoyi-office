package cn.iocoder.yudao.framework.web.core.handler;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerUnreadableTest {

    static class Box {
        public Integer initiatorWithdrawMode;
    }

    @Test
    void booleanAndArrayBodyMapToAppCode400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler("test-app", mock(ApiErrorLogCommonApi.class));
        ObjectMapper om = new ObjectMapper();
        for (String json : new String[]{
                "{\"initiatorWithdrawMode\":true}",
                "{\"initiatorWithdrawMode\":[]}"
        }) {
            JsonProcessingException cause;
            try {
                om.readValue(json, Box.class);
                fail("expected unreadable: " + json);
                return;
            } catch (JsonProcessingException e) {
                cause = e;
            }
            HttpMessageNotReadableException ex =
                    new HttpMessageNotReadableException("JSON parse error", cause, mock(HttpInputMessage.class));
            CommonResult<?> result = handler.methodArgumentTypeInvalidFormatExceptionHandler(ex);
            assertEquals(400, result.getCode(), json + " -> " + result.getMsg());
        }
    }
}
