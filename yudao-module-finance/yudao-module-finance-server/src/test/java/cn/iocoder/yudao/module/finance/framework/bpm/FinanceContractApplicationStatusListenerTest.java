package cn.iocoder.yudao.module.finance.framework.bpm;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PAY-R11 L1：合同 StatusListener 回归 */
class FinanceContractApplicationStatusListenerTest {

    private FinanceContractApplicationService contractService;
    private FinanceContractApplicationMapper mapper;
    private FinanceContractApplicationStatusListener listener;

    @BeforeEach
    void setUp() {
        contractService = mock(FinanceContractApplicationService.class);
        mapper = mock(FinanceContractApplicationMapper.class);
        listener = new FinanceContractApplicationStatusListener();
        ReflectionTestUtils.setField(listener, "contractApplicationService", contractService);
        ReflectionTestUtils.setField(listener, "contractApplicationMapper", mapper);
    }

    @Test
    void onEventWithTenantCallsOutcome() {
        listener.onEvent(rejectEvent("5", "9", "pi-c"));
        verify(contractService).onApprovalOutcome(9L,
                FinanceContractApprovalStatusEnum.REJECTED.getStatus(), "pi-c");
    }

    @Test
    void onEventBlankPiFailClosed() {
        assertThrows(IllegalStateException.class, () -> listener.onEvent(rejectEvent("1", "9", "")));
        verify(contractService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
    }

    @Test
    void onEventTenantZeroFailClosed() {
        assertThrows(IllegalStateException.class, () -> listener.onEvent(rejectEvent("0", "9", "pi-c")));
        verify(contractService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
    }

    @Test
    void onEventResolveRequiresMatchingPi() {
        FinanceContractApplicationDO row = FinanceContractApplicationDO.builder()
                .id(9L).processInstanceId("pi-old").build();
        row.setTenantId(2L);
        when(mapper.selectById(9L)).thenReturn(row);
        assertThrows(IllegalStateException.class, () -> listener.onEvent(rejectEvent(null, "9", "pi-new")));
        verify(contractService, never()).onApprovalOutcome(anyLong(), anyString(), anyString());
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
