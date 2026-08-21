package cn.iocoder.yudao.module.hrm.service.payroll;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayslipAccessTest {

    @Test
    void userCannotReadAnotherSnapshot() {
        assertTrue(PayslipAccess.canReadOwn(8L, 8L));
        assertFalse(PayslipAccess.canReadOwn(8L, 9L));
        assertFalse(PayslipAccess.canReadOwn(null, 8L));
    }

    @Test
    void batchPermissionDoesNotWidenPayslip() {
        assertTrue(PayslipAccess.payslipIgnoresBatchPermission(true));
    }
}
