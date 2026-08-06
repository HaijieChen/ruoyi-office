package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PAY-R11：付款 StatusListener PI 绑定 */
class FinancePaymentApplicationStatusListenerTest {

    private FinancePaymentApplicationService paymentService;
    private FinancePaymentApplicationMapper mapper;
    private FinancePaymentApplicationStatusListener listener;

    @BeforeEach
    void setUp() {
        paymentService = mock(FinancePaymentApplicationService.class);
        mapper = mock(FinancePaymentApplicationMapper.class);
        listener = new FinancePaymentApplicationStatusListener();
        ReflectionTestUtils.setField(listener, "paymentApplicationService", paymentService);
        ReflectionTestUtils.setField(listener, "paymentApplicationMapper", mapper);
    }

    @Test
    void onEventWithTenantAndPiCallsOutcome() {
        listener.onEvent(rejectEvent("10", "88", "pi-x"));
        verify(paymentService).onApprovalOutcome(88L,
                FinancePaymentApplicationStatusEnum.REJECTED.getStatus(), "pi-x");
        verify(mapper, never()).selectById(anyLong());
    }

    @Test
    void onEventBlankPiFailClosed() {
        assertThrows(IllegalStateException.class, () -> listener.onEvent(rejectEvent("1", "88", null)));
        verify(paymentService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
    }

    @Test
    void onEventBlankRowPiFailClosedWhenResolving() {
        FinancePaymentApplicationDO row = FinancePaymentApplicationDO.builder()
                .id(88L).processInstanceId(null).build();
        row.setTenantId(3L);
        when(mapper.selectById(88L)).thenReturn(row);
        assertThrows(IllegalStateException.class, () -> listener.onEvent(rejectEvent(null, "88", "pi-x")));
        verify(paymentService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
    }

    @Test
    void onEventPiMismatchFailClosed() {
        FinancePaymentApplicationDO row = FinancePaymentApplicationDO.builder()
                .id(77L).processInstanceId("pi-old").build();
        row.setTenantId(1L);
        when(mapper.selectById(77L)).thenReturn(row);
        assertThrows(IllegalStateException.class, () -> listener.onEvent(rejectEvent(null, "77", "pi-new")));
        verify(paymentService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
    }

    @Test
    void onEventRowTenantZeroFailClosed() {
        FinancePaymentApplicationDO row = FinancePaymentApplicationDO.builder()
                .id(77L).processInstanceId("pi-z").build();
        row.setTenantId(0L);
        when(mapper.selectById(77L)).thenReturn(row);
        assertThrows(IllegalStateException.class, () -> listener.onEvent(rejectEvent(null, "77", "pi-z")));
        verify(paymentService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
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
