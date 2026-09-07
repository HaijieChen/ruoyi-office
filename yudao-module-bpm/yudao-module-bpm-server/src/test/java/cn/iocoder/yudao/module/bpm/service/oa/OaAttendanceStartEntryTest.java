package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceCreateReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.bpm.framework.security.BpmBusinessStartCallerGuard;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessStartEligibilityService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaAttendanceStartEntryTest {

    private static final String PAST_GUARD = "PAST_ATTENDANCE_GUARD";

    private BpmProcessInstanceServiceImpl service;
    private BpmProcessDefinitionService processDefinitionService;
    private HistoryService historyService;
    private RuntimeService runtimeService;
    private BpmBusinessStartCallerGuard callerGuard;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = spy(new BpmProcessInstanceServiceImpl());
        processDefinitionService = mock(BpmProcessDefinitionService.class);
        historyService = mock(HistoryService.class);
        runtimeService = mock(RuntimeService.class);
        BpmProcessStartEligibilityService eligibility = mock(BpmProcessStartEligibilityService.class);
        callerGuard = mock(BpmBusinessStartCallerGuard.class);
        ReflectionTestUtils.setField(service, "processDefinitionService", processDefinitionService);
        ReflectionTestUtils.setField(service, "processStartEligibilityService", eligibility);
        ReflectionTestUtils.setField(service, "historyService", historyService);
        ReflectionTestUtils.setField(service, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(service, "businessStartCallerGuard", callerGuard);
        doNothing().when(eligibility).validateStartOrThrow(anyString(), any(Boolean.class));
        doNothing().when(callerGuard).requireVerifiedFinanceCaller();
        BpmApprovalDetailRespVO detail = new BpmApprovalDetailRespVO();
        detail.setActivityNodes(new ArrayList<>());
        doReturn(detail).when(service).getApprovalDetail(anyLong(), any(BpmApprovalDetailReqVO.class));
        HistoricProcessInstanceQuery query = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceTenantId(any())).thenReturn(query);
        when(query.processInstanceBusinessKey(anyString())).thenReturn(query);
        when(query.list()).thenReturn(List.of());
        when(runtimeService.createProcessInstanceBuilder()).thenThrow(new IllegalStateException(PAST_GUARD));
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void voCreate_overtimeWithoutHolder_rejectsBeforeDeleteHistoric() {
        stubDefinitionById("def-ot", "oa_overtime");
        BpmProcessInstanceCreateReqVO vo = new BpmProcessInstanceCreateReqVO();
        vo.setProcessDefinitionId("def-ot");
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createProcessInstance(1L, vo));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_USER_CAN_START.getCode(), ex.getCode());
        verify(historyService, never()).createHistoricProcessInstanceQuery();
        verify(historyService, never()).deleteHistoricProcessInstance(anyString());
        verify(runtimeService, never()).createProcessInstanceBuilder();
    }

    @Test
    void dtoCreate_punchWithoutHolder_rejectsBeforeDeleteHistoric() {
        stubActiveDefinition("oa_punch_correction");
        BpmProcessInstanceCreateReqDTO dto = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey("oa_punch_correction")
                .setBusinessKey("99");
        ServiceException ex = unwrapServiceException(() -> service.createProcessInstance(1L, dto));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_USER_CAN_START.getCode(), ex.getCode());
        verify(historyService, never()).createHistoricProcessInstanceQuery();
        verify(historyService, never()).deleteHistoricProcessInstance(anyString());
        verify(runtimeService, never()).createProcessInstanceBuilder();
    }

    @Test
    void byBusiness_overtimeWithFinanceGuardPass_stillRejectsBeforeDeleteHistoric() {
        stubActiveDefinition("oa_overtime");
        BpmProcessInstanceCreateReqDTO dto = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey("oa_overtime")
                .setBusinessKey("7");
        ServiceException ex = unwrapServiceException(() -> service.createProcessInstanceByBusiness(1L, dto));
        assertEquals(ErrorCodeConstants.PROCESS_INSTANCE_START_USER_CAN_START.getCode(), ex.getCode());
        verify(callerGuard).requireVerifiedFinanceCaller();
        verify(historyService, never()).createHistoricProcessInstanceQuery();
        verify(historyService, never()).deleteHistoricProcessInstance(anyString());
    }

    @Test
    void dtoCreate_outingWithoutAttendanceHolder_passesGuardAndReachesHistoricDelete() {
        stubActiveDefinition("oa_outing");
        BpmProcessInstanceCreateReqDTO dto = new BpmProcessInstanceCreateReqDTO()
                .setProcessDefinitionKey("oa_outing")
                .setBusinessKey("outing-1");
        RuntimeException wrapped = assertThrows(RuntimeException.class,
                () -> service.createProcessInstance(1L, dto));
        assertEquals(PAST_GUARD, rootCause(wrapped).getMessage());
        verify(historyService).createHistoricProcessInstanceQuery();
    }

    private static ServiceException unwrapServiceException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected reject");
        } catch (ServiceException ex) {
            return ex;
        } catch (RuntimeException ex) {
            Throwable cause = rootCause(ex);
            if (cause instanceof ServiceException serviceException) {
                return serviceException;
            }
            throw ex;
        }
    }

    private static Throwable rootCause(Throwable ex) {
        Throwable cur = ex;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private void stubDefinitionById(String defId, String key) {
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn(defId);
        when(definition.getKey()).thenReturn(key);
        when(definition.isSuspended()).thenReturn(false);
        when(processDefinitionService.getProcessDefinition(defId)).thenReturn(definition);
        stubInfo();
    }

    private void stubActiveDefinition(String key) {
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn("def-" + key);
        when(definition.getKey()).thenReturn(key);
        when(definition.isSuspended()).thenReturn(false);
        when(processDefinitionService.getActiveProcessDefinition(key)).thenReturn(definition);
        stubInfo();
    }

    private void stubInfo() {
        BpmProcessDefinitionInfoDO info = new BpmProcessDefinitionInfoDO();
        when(processDefinitionService.getProcessDefinitionInfo(any())).thenReturn(info);
        when(processDefinitionService.canUserStartProcessDefinition(any(), anyLong())).thenReturn(true);
    }
}
