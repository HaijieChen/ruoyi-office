package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * PAY-R8：主路径 end delegate — 状态映射 + 同步写台账（不经 async listener）。
 */
class FinancePaymentApprovalOutcomeDelegateTest {

    private FinancePaymentApplicationService paymentService;
    private FinancePaymentApprovalOutcomeDelegate delegate;

    @BeforeEach
    void setUp() {
        paymentService = mock(FinancePaymentApplicationService.class);
        delegate = new FinancePaymentApprovalOutcomeDelegate();
        ReflectionTestUtils.setField(delegate, "paymentApplicationService", paymentService);
    }

    @Test
    void mapRejectToRejected() {
        assertEquals(FinancePaymentApplicationStatusEnum.REJECTED.getStatus(),
                FinancePaymentApprovalOutcomeDelegate.mapProcessStatusToOutcome(
                        BpmProcessInstanceStatusEnum.REJECT.getStatus()));
    }

    @Test
    void mapCancelToCancelled() {
        assertEquals(FinancePaymentApplicationStatusEnum.CANCELLED.getStatus(),
                FinancePaymentApprovalOutcomeDelegate.mapProcessStatusToOutcome(
                        BpmProcessInstanceStatusEnum.CANCEL.getStatus()));
    }

    @Test
    void executeRejectWritesRejectedWithPi() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("42");
        when(execution.getProcessInstanceId()).thenReturn("pi-reject");
        when(execution.getVariable(FinancePaymentApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE))
                .thenReturn(BpmProcessInstanceStatusEnum.REJECT.getStatus());

        delegate.execute(execution);

        verify(paymentService).onApprovalOutcome(42L,
                FinancePaymentApplicationStatusEnum.REJECTED.getStatus(), "pi-reject");
    }

    @Test
    void executeCancelWritesCancelledWithPi() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getProcessInstanceBusinessKey()).thenReturn("43");
        when(execution.getProcessInstanceId()).thenReturn("pi-cancel");
        when(execution.getVariable(FinancePaymentApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE))
                .thenReturn(BpmProcessInstanceStatusEnum.CANCEL.getStatus());

        delegate.execute(execution);

        verify(paymentService).onApprovalOutcome(43L,
                FinancePaymentApplicationStatusEnum.CANCELLED.getStatus(), "pi-cancel");
    }
}
