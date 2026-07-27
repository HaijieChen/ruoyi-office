package cn.iocoder.yudao.module.finance.service.receipt;

import cn.iocoder.yudao.module.finance.controller.admin.receipt.FinanceReceiptController;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptLifecycleAuditRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptLifecycleReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FinanceReceiptControllerContractTest {

    @Test
    void importTemplateEndpointShouldUseImportPermission() throws NoSuchMethodException {
        Method method = FinanceReceiptController.class.getDeclaredMethod(
                "importTemplate", HttpServletResponse.class);

        assertArrayEquals(new String[]{"/get-import-template"}, method.getAnnotation(GetMapping.class).value());
        assertEquals("@ss.hasPermission('finance:receipt:import')",
                method.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void closeAndReopenEndpointsShouldUseDedicatedFinancePermissions() throws NoSuchMethodException {
        Method close = FinanceReceiptController.class.getDeclaredMethod(
                "closeReceipt", FinanceReceiptLifecycleReqVO.class);
        Method reopen = FinanceReceiptController.class.getDeclaredMethod(
                "reopenReceipt", FinanceReceiptLifecycleReqVO.class);

        assertArrayEquals(new String[]{"/close"}, close.getAnnotation(PutMapping.class).value());
        assertEquals("@ss.hasPermission('finance:receipt:close')",
                close.getAnnotation(PreAuthorize.class).value());
        assertArrayEquals(new String[]{"/reopen"}, reopen.getAnnotation(PutMapping.class).value());
        assertEquals("@ss.hasPermission('finance:receipt:reopen')",
                reopen.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void lifecycleAuditEndpointShouldUseDedicatedFinancePermission() throws NoSuchMethodException {
        Method method = FinanceReceiptController.class.getDeclaredMethod("getLifecycleAuditList", Long.class);

        assertArrayEquals(new String[]{"/lifecycle-audit-list"}, method.getAnnotation(GetMapping.class).value());
        assertEquals("@ss.hasPermission('finance:receipt:audit-query')",
                method.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void lifecycleAuditResponseShouldExposeOnlyImmutableAuditFields() throws NoSuchFieldException {
        assertEquals(List.of("id", "receiptId", "action", "operatorId", "actionTime", "reason"),
                Stream.of(FinanceReceiptLifecycleAuditRespVO.class.getDeclaredFields())
                        .map(java.lang.reflect.Field::getName).toList());
        assertEquals(Integer.class, FinanceReceiptLifecycleAuditRespVO.class.getDeclaredField("action").getType());
        assertEquals(LocalDateTime.class,
                FinanceReceiptLifecycleAuditRespVO.class.getDeclaredField("actionTime").getType());
    }
}
