package cn.iocoder.yudao.module.finance.controller.admin.invoice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceInvoiceRedflushGetAuthTest {

    @Test
    void getDoesNotRequireInvoiceQueryMenu() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/cn/iocoder/yudao/module/finance/controller/admin/invoice/FinanceInvoiceRedflushController.java"));
        int idx = src.indexOf("@GetMapping(\"/get\")");
        assertTrue(idx >= 0);
        String block = src.substring(idx, src.indexOf("@PostMapping(\"/complete-issue\")"));
        assertFalse(block.contains("finance:invoice-application:query"));
        assertTrue(block.contains("isAuthenticated()"));
    }
}
