package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FinanceInvoiceApprovalOutcomeDelegateTest {

    @Test
    void runningOrNullAtEndMeansApproved() {
        assertEquals(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus(),
                FinanceInvoiceApprovalOutcomeDelegate.mapProcessStatusToOutcome(1));
        assertEquals(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus(),
                FinanceInvoiceApprovalOutcomeDelegate.mapProcessStatusToOutcome(null));
    }

    @Test
    void explicitTerminalStatuses() {
        assertEquals(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus(),
                FinanceInvoiceApprovalOutcomeDelegate.mapProcessStatusToOutcome(2));
        assertEquals(FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus(),
                FinanceInvoiceApprovalOutcomeDelegate.mapProcessStatusToOutcome(3));
        assertEquals(FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus(),
                FinanceInvoiceApprovalOutcomeDelegate.mapProcessStatusToOutcome(4));
        assertEquals(FinanceInvoiceApprovalStatusEnum.CANCELLED.getStatus(),
                FinanceInvoiceApprovalOutcomeDelegate.mapProcessStatusToOutcome(10));
    }

    @Test
    void unknownStatusReturnsNull() {
        assertNull(FinanceInvoiceApprovalOutcomeDelegate.mapProcessStatusToOutcome(99));
    }
}
