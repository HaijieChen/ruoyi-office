package cn.iocoder.yudao.module.hrm.service.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollBatchRulesTest {

    @Test
    void ae8_draftCannotBeSeenAsPublished() {
        assertTrue(PayrollBatchRules.canEdit(PayrollBatchRules.DRAFT));
        assertFalse(PayrollBatchRules.canEdit(PayrollBatchRules.PUBLISHED));
        assertEquals(PayrollBatchRules.PUBLISHED, PayrollBatchRules.publish(PayrollBatchRules.DRAFT));
        assertEquals(PayrollBatchRules.DRAFT, PayrollBatchRules.withdraw(PayrollBatchRules.PUBLISHED));
        assertThrows(IllegalStateException.class, () -> PayrollBatchRules.publish(PayrollBatchRules.PUBLISHED));
    }

    @Test
    void duplicateNamesAreNotUnique() {
        assertFalse(PayrollBatchRules.uniqueExactName(List.of("张三", "张三"), "张三"));
        assertTrue(PayrollBatchRules.uniqueExactName(List.of("张三", "李四"), "张三"));
        assertFalse(PayrollBatchRules.uniqueExactName(List.of("张三"), "王五"));
    }

    @Test
    void ytdAddsOpeningPublishedAndUncoveredOa() {
        BigDecimal ytd = PayrollBatchRules.yearToDateSick(
                new BigDecimal("4"),
                List.of(new BigDecimal("3"), new BigDecimal("2")),
                new BigDecimal("1")
        );
        assertEquals(new BigDecimal("10"), ytd);
    }
}
