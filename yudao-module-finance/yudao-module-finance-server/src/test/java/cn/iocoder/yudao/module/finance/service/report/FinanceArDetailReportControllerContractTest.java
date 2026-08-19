package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.FinanceArDetailReportController;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceArDetailReportControllerContractTest {

    @Test
    void pageEndpointShouldUseArQueryPermissionAndPath() throws NoSuchMethodException {
        RequestMapping mapping = FinanceArDetailReportController.class.getAnnotation(RequestMapping.class);
        assertEquals("/finance/report/ar-detail", mapping.value()[0]);

        Method method = FinanceArDetailReportController.class.getDeclaredMethod(
                "getPage", FinanceArDetailReportPageReqVO.class);
        assertTrue(Arrays.asList(method.getAnnotation(GetMapping.class).value()).contains("/page"));
        assertEquals("@ss.hasPermission('finance:report-ar:query')",
                method.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void queryAllPermissionIsDedicatedFinanceAdminPermission() throws Exception {
        Field field = FinanceArDetailReportController.class.getDeclaredField("QUERY_ALL_PERMISSION");
        field.setAccessible(true);
        assertEquals("finance:report-ar:query-all", field.get(null));
    }
}
