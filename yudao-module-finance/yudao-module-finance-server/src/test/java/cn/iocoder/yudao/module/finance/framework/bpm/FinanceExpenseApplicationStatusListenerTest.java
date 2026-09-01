package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpenseReimbursementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void onEventWithTenantAndPiCallsRejectedOutcome() {
        listener.onEvent(rejectEvent("10", "88", "pi-x"));
        verify(expenseService).onApprovalOutcome(88L,
                FinanceExpenseReimbursementDO.STATUS_REJECTED, "pi-x");
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void onEventBlankPiFailClosed() {
        assertThrows(IllegalStateException.class, () -> listener.onEvent(rejectEvent("1", "88", null)));
        verify(expenseService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
    }

    private static BpmProcessInstanceStatusEvent rejectEvent(String tenantId, String businessKey, String pi) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent();
        BpmProcessInstanceInfo info = new BpmProcessInstanceInfo();
        info.setTenantId(tenantId);
        info.setBusinessKey(businessKey);
        info.setProcessInstanceId(pi);
        info.setStatus(BpmProcessInstanceStatusEnum.REJECT.getStatus());
        event.setProcessInstanceInfo(info);
        return event;
    }
}
