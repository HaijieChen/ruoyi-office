package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FinanceExpenseApplicationStatusListenerTest {

    private FinanceExpenseReimbursementService expenseService;
    private FinanceExpenseReimbursementMapper mapper;
    private FinanceExpenseApplicationStatusListener listener;

    @BeforeEach
    void setUp() {
        expenseService = mock(FinanceExpenseReimbursementService.class);
        mapper = mock(FinanceExpenseReimbursementMapper.class);
        listener = new FinanceExpenseApplicationStatusListener();
        ReflectionTestUtils.setField(listener, "expenseReimbursementService", expenseService);
        ReflectionTestUtils.setField(listener, "expenseReimbursementMapper", mapper);
    }

    @Test
    void withInvoiceKeyIsExpenseReimbursement() {
        assertEquals(FinanceExpenseReimbursementService.PROCESS_KEY,
                listener.getProcessDefinitionKey());
    }

    @Test
    void noInvoiceKeyIsExpenseNoInvoice() {
        assertEquals(FinanceExpenseReimbursementService.PROCESS_KEY_NO_INVOICE,
                new FinanceExpenseNoInvoiceApplicationStatusListener().getProcessDefinitionKey());
    }

    @Test
    void mapRejectToRejected() {
        assertEquals(FinanceExpenseReimbursementDO.STATUS_REJECTED,
                FinanceExpenseProcessStatusMapper.toOutcome(
                        BpmProcessInstanceStatusEnum.REJECT.getStatus()));
    }

    @Test
    void mapCancelToCancelled() {
        assertEquals(FinanceExpenseReimbursementDO.STATUS_CANCELLED,
                FinanceExpenseProcessStatusMapper.toOutcome(
                        BpmProcessInstanceStatusEnum.CANCEL.getStatus()));
    }

    @Test
    void mapWithdrawToCancelled() {
        assertEquals(FinanceExpenseReimbursementDO.STATUS_CANCELLED,
                FinanceExpenseProcessStatusMapper.toOutcome(
                        BpmTaskStatusEnum.WITHDRAW.getStatus()));
    }

    @Test
    void mapApproveDoesNotReleaseOccupancy() {
        assertNull(FinanceExpenseProcessStatusMapper.toOutcome(
                BpmProcessInstanceStatusEnum.APPROVE.getStatus()));
        assertNull(FinanceExpenseProcessStatusMapper.toOutcome(
                BpmProcessInstanceStatusEnum.RUNNING.getStatus()));
        assertNull(FinanceExpenseProcessStatusMapper.toOutcome(null));
    }

    @Test
    void onEventWithTenantAndPiCallsRejectedOutcome() {
        listener.onEvent(statusEvent("10", "88", "pi-x", BpmProcessInstanceStatusEnum.REJECT.getStatus()));
        verify(expenseService).onApprovalOutcome(88L,
                FinanceExpenseReimbursementDO.STATUS_REJECTED, "pi-x");
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void onEventCancelWritesCancelled() {
        listener.onEvent(statusEvent("10", "43", "pi-cancel", BpmProcessInstanceStatusEnum.CANCEL.getStatus()));
        verify(expenseService).onApprovalOutcome(43L,
                FinanceExpenseReimbursementDO.STATUS_CANCELLED, "pi-cancel");
    }

    @Test
    void onEventApproveDoesNotWriteHeader() {
        listener.onEvent(statusEvent("10", "88", "pi-ok", BpmProcessInstanceStatusEnum.APPROVE.getStatus()));
        verify(expenseService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
    }

    @Test
    void onEventBlankPiFailClosed() {
        assertThrows(IllegalStateException.class,
                () -> listener.onEvent(statusEvent("1", "88", null, BpmProcessInstanceStatusEnum.REJECT.getStatus())));
        verify(expenseService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
    }

    private static BpmProcessInstanceStatusEvent statusEvent(String tenantId, String businessKey,
                                                             String pi, Integer status) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent();
        BpmProcessInstanceInfo info = new BpmProcessInstanceInfo();
        info.setTenantId(tenantId);
        info.setBusinessKey(businessKey);
        info.setProcessInstanceId(pi);
        info.setStatus(status);
        event.setProcessInstanceInfo(info);
        return event;
    }
}
