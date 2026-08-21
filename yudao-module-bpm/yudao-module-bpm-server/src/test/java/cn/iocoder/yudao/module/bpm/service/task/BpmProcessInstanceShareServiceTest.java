package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceShareDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.task.BpmProcessInstanceShareMapper;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.history.HistoricProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BpmProcessInstanceShareServiceTest {

    @Mock
    private BpmProcessInstanceShareMapper shareMapper;
    @Mock
    private BpmProcessInstanceService processInstanceService;
    @Mock
    private AdminUserApi adminUserApi;

    @InjectMocks
    private BpmProcessInstanceShareServiceImpl shareService;

    private HistoricProcessInstance approved;

    @BeforeEach
    void setUp() {
        approved = mock(HistoricProcessInstance.class);
        org.mockito.Mockito.lenient().when(approved.getStartUserId()).thenReturn("1");
        org.mockito.Mockito.lenient().when(approved.getName()).thenReturn("合同审批");
        org.mockito.Mockito.lenient().when(approved.getProcessVariables()).thenReturn(
                Map.of(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                        BpmProcessInstanceStatusEnum.APPROVE.getStatus()));
    }

    @Test
    void shareRejectsNonInitiator() {
        when(processInstanceService.getHistoricProcessInstance("pi-1")).thenReturn(approved);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> shareService.share(99L, "pi-1", List.of(2L)));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_SHARE_NOT_INITIATOR.getCode(), ex.getCode());
        verify(shareMapper, never()).insert(any(BpmProcessInstanceShareDO.class));
    }

    @Test
    void shareRejectsRejectedInstance() {
        HistoricProcessInstance rejected = mock(HistoricProcessInstance.class);
        when(rejected.getStartUserId()).thenReturn("1");
        when(rejected.getProcessVariables()).thenReturn(
                Map.of(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                        BpmProcessInstanceStatusEnum.REJECT.getStatus()));
        when(processInstanceService.getHistoricProcessInstance("pi-1")).thenReturn(rejected);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> shareService.share(1L, "pi-1", List.of(2L)));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_SHARE_NOT_APPROVED.getCode(), ex.getCode());
    }

    @Test
    void shareRejectsCancelledInstance() {
        HistoricProcessInstance cancelled = mock(HistoricProcessInstance.class);
        when(cancelled.getStartUserId()).thenReturn("1");
        when(cancelled.getProcessVariables()).thenReturn(
                Map.of(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                        BpmProcessInstanceStatusEnum.CANCEL.getStatus()));
        when(processInstanceService.getHistoricProcessInstance("pi-1")).thenReturn(cancelled);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> shareService.share(1L, "pi-1", List.of(2L)));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_SHARE_NOT_APPROVED.getCode(), ex.getCode());
    }

    @Test
    void shareInsertsThenUnrevokesSameRow() {
        when(processInstanceService.getHistoricProcessInstance("pi-1")).thenReturn(approved);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(2L);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus());
        @SuppressWarnings("unchecked")
        CommonResult<AdminUserRespDTO> result = mock(CommonResult.class);
        when(result.getCheckedData()).thenReturn(user);
        when(adminUserApi.getUser(2L)).thenReturn(result);
        when(shareMapper.selectByInstanceAndRecipient("pi-1", 2L)).thenReturn(null);

        shareService.share(1L, "pi-1", List.of(2L));
        ArgumentCaptor<BpmProcessInstanceShareDO> cap = ArgumentCaptor.forClass(BpmProcessInstanceShareDO.class);
        verify(shareMapper).insert(cap.capture());
        assertEquals(2L, cap.getValue().getRecipientUserId());

        BpmProcessInstanceShareDO revoked = BpmProcessInstanceShareDO.builder()
                .id(9L)
                .processInstanceId("pi-1")
                .recipientUserId(2L)
                .revokedAt(LocalDateTime.now())
                .build();
        when(shareMapper.selectByInstanceAndRecipient("pi-1", 2L)).thenReturn(revoked);
        shareService.share(1L, "pi-1", List.of(2L));
        verify(shareMapper).updateById(revoked);
        assertEquals(null, revoked.getRevokedAt());
    }

    @Test
    void recipientCannotRevoke() {
        when(processInstanceService.getHistoricProcessInstance("pi-1")).thenReturn(approved);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> shareService.revoke(2L, "pi-1", 2L));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_SHARE_NOT_INITIATOR.getCode(), ex.getCode());
    }

    @Test
    void nonInitiatorCannotListRecipients() {
        when(processInstanceService.getHistoricProcessInstance("pi-1")).thenReturn(approved);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> shareService.getActiveRecipients(2L, "pi-1"));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_SHARE_NOT_INITIATOR.getCode(), ex.getCode());
    }

    @Test
    void canAccessRelatedIsStarterOrShareNotCopy() {
        when(processInstanceService.getHistoricProcessInstance("pi-1")).thenReturn(approved);
        org.junit.jupiter.api.Assertions.assertTrue(shareService.canAccessRelated(1L, "pi-1"));
        when(shareMapper.selectByInstanceAndRecipient("pi-1", 2L)).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertFalse(shareService.canAccessRelated(2L, "pi-1"));
        when(shareMapper.selectByInstanceAndRecipient("pi-1", 2L)).thenReturn(
                BpmProcessInstanceShareDO.builder().recipientUserId(2L).revokedAt(null).build());
        org.junit.jupiter.api.Assertions.assertTrue(shareService.canAccessRelated(2L, "pi-1"));
    }
}
