package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceExpenseApprovalOutcomeDelegateTest {

    private FinanceExpenseReimbursementService expenseService;
    private FinanceExpenseApprovalOutcomeDelegate delegate;

    @BeforeEach
    void setUp() {
        expenseService = mock(FinanceExpenseReimbursementService.class);
        delegate = new FinanceExpenseApprovalOutcomeDelegate();
        ReflectionTestUtils.setField(delegate, "expenseReimbursementService", expenseService);
    }

    @Test
    void mapRejectToRejected() {
        assertEquals(FinanceExpenseReimbursementDO.STATUS_REJECTED,
                FinanceExpenseApprovalOutcomeDelegate.mapProcessStatusToOutcome(
                        BpmProcessInstanceStatusEnum.REJECT.getStatus()));
    }

    @Test
    void mapCancelToCancelled() {
        assertEquals(FinanceExpenseReimbursementDO.STATUS_CANCELLED,
                FinanceExpenseApprovalOutcomeDelegate.mapProcessStatusToOutcome(
                        BpmProcessInstanceStatusEnum.CANCEL.getStatus()));
    }

    @Test
    void mapWithdrawToCancelled() {
        assertEquals(FinanceExpenseReimbursementDO.STATUS_CANCELLED,
                FinanceExpenseApprovalOutcomeDelegate.mapProcessStatusToOutcome(
                        BpmTaskStatusEnum.WITHDRAW.getStatus()));
    }

    @Test
    void mapApproveDoesNotReleaseOccupancy() {
        assertNull(FinanceExpenseApprovalOutcomeDelegate.mapProcessStatusToOutcome(
                BpmProcessInstanceStatusEnum.APPROVE.getStatus()));
        assertNull(FinanceExpenseApprovalOutcomeDelegate.mapProcessStatusToOutcome(
                BpmProcessInstanceStatusEnum.RUNNING.getStatus()));
        assertNull(FinanceExpenseApprovalOutcomeDelegate.mapProcessStatusToOutcome(null));
    }

    @Test
    void executeRejectWritesRejectedWithPi() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("42");
        when(execution.getProcessInstanceId()).thenReturn("pi-reject");
        when(execution.getVariable(FinanceExpenseApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE))
                .thenReturn(BpmProcessInstanceStatusEnum.REJECT.getStatus());

        delegate.execute(execution);

        verify(expenseService).onApprovalOutcome(42L,
                FinanceExpenseReimbursementDO.STATUS_REJECTED, "pi-reject");
    }

    @Test
    void executeCancelWritesCancelledWithPi() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("43");
        when(execution.getProcessInstanceId()).thenReturn("pi-cancel");
        when(execution.getVariable(FinanceExpenseApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE))
                .thenReturn(BpmProcessInstanceStatusEnum.CANCEL.getStatus());

        delegate.execute(execution);

        verify(expenseService).onApprovalOutcome(43L,
                FinanceExpenseReimbursementDO.STATUS_CANCELLED, "pi-cancel");
    }
}
