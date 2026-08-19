package cn.iocoder.yudao.module.finance.controller.admin.opening;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class FinanceBankOpeningBalanceControllerContractTest {

    private static final String QUERY = "finance:report-bank-balance:query";
    private static final String UPDATE = "finance:bank-opening:update";

    @Test
    void controllerIsMappedUnderBankOpeningBalance() {
        RequestMapping mapping = FinanceBankOpeningBalanceController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/finance/bank-opening-balance"}, mapping.value());
    }

    @Test
    void queryPermissionCannotWrite() {
        for (Method method : FinanceBankOpeningBalanceController.class.getDeclaredMethods()) {
            PreAuthorize pa = method.getAnnotation(PreAuthorize.class);
            if (pa == null) {
                continue;
            }
            String expr = pa.value();
            boolean isGet = method.getAnnotation(GetMapping.class) != null;
            boolean isWrite = method.getAnnotation(PostMapping.class) != null
                    || method.getAnnotation(PutMapping.class) != null;
            if (isGet) {
                assertTrue(expr.contains(QUERY), method.getName() + " GET must use query: " + expr);
                assertFalse(expr.contains(UPDATE),
                        "query permission must not grant write on " + method.getName() + ": " + expr);
            }
            if (isWrite) {
                assertTrue(expr.contains(UPDATE), method.getName() + " write must use update: " + expr);
                assertFalse(expr.contains(QUERY),
                        "query permission cannot write via " + method.getName() + ": " + expr);
            }
        }
    }

}
