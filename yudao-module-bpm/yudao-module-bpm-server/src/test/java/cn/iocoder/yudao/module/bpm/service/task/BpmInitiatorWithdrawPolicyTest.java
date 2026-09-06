package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmInitiatorWithdrawPolicyTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "null,false,0", "null,true,2", "null,null,2",
            "0,false,0", "0,true,0", "0,null,0",
            "1,false,1", "1,true,1", "1,null,1",
            "2,false,2", "2,true,2", "2,null,2"}, nullValues = "null")
    void explicitModesOverrideLegacyAndNullPreservesCompatibility(Integer mode, Boolean legacy, int expected) {
        assertEquals(expected, BpmInitiatorWithdrawPolicyService.resolveMode(
                new BpmProcessDefinitionInfoDO().setInitiatorWithdrawMode(mode).setAllowWithdrawTask(legacy)));
    }

    @Test
    void disabledInitiatorModeMustDenyEvenWhenApproverWithdrawalAllowed() {
        BpmTaskServiceImpl service = new BpmTaskServiceImpl();
        RuntimeService runtime = mock(RuntimeService.class);
        ProcessInstanceQuery query = mock(ProcessInstanceQuery.class, RETURNS_SELF);
        ProcessInstance instance = mock(ProcessInstance.class);
        when(runtime.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(instance);
        when(instance.getId()).thenReturn("instance");
        when(instance.getProcessInstanceId()).thenReturn("instance");
        when(instance.getProcessDefinitionId()).thenReturn("definition");
        when(instance.getStartUserId()).thenReturn("1");
        when(instance.getTenantId()).thenReturn("1");
        BpmProcessDefinitionService definitions = mock(BpmProcessDefinitionService.class);
        when(definitions.getProcessDefinitionInfo("definition")).thenReturn(
                new BpmProcessDefinitionInfoDO().setInitiatorWithdrawMode(0).setAllowWithdrawTask(true));
        ReflectionTestUtils.setField(service, "runtimeService", runtime);
        ReflectionTestUtils.setField(service, "bpmProcessDefinitionService", definitions);
        BpmInitiatorWithdrawPolicyService guard = mock(BpmInitiatorWithdrawPolicyService.class);
        doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(guard).withWithdrawalLock(anyString(), any());
        ReflectionTestUtils.setField(service, "initiatorWithdrawPolicyService", guard);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(1L);
        try {
            // Test the real service boundary; state/transaction guard is tested against MySQL separately.
            ServiceException failure = assertThrows(ServiceException.class,
                    () -> service.withdrawProcessToStart(1L, "instance", "test"));
            assertEquals(cn.iocoder.yudao.module.bpm.enums.BpmInitiatorWithdrawErrorCodeConstants
                    .INITIATOR_WITHDRAW_DISABLED.getCode(), failure.getCode());
        } finally {
            cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
        }
    }
    @Test
    void automaticCallbackRetriesOnlyGenerationConflictsAndStopsAfterThreeAttempts() {
        BpmInitiatorWithdrawPolicyService service = new BpmInitiatorWithdrawPolicyService();
        BpmInitiatorWithdrawPolicyService transactional = mock(BpmInitiatorWithdrawPolicyService.class);
        Runnable action = () -> fail("mock transaction must not execute action");
        try (org.mockito.MockedStatic<cn.hutool.extra.spring.SpringUtil> beans = mockStatic(cn.hutool.extra.spring.SpringUtil.class)) {
            beans.when(() -> cn.hutool.extra.spring.SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(transactional);
            ServiceException conflict = cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.module.bpm.enums.BpmInitiatorWithdrawErrorCodeConstants.INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
            doThrow(conflict).when(transactional).runAfterCompletionInTransaction("instance", action);
            assertSame(conflict, assertThrows(ServiceException.class,
                    () -> service.runAfterCompletion("instance", "1", "task", action)));
            verify(transactional, times(3)).runAfterCompletionInTransaction("instance", action);
            reset(transactional);
            IllegalStateException other = new IllegalStateException("non-concurrency failure");
            doThrow(other).when(transactional).runAfterCompletionInTransaction("instance", action);
            assertSame(other, assertThrows(IllegalStateException.class,
                    () -> service.runAfterCompletion("instance", "1", "task", action)));
            verify(transactional).runAfterCompletionInTransaction("instance", action);
        }
    }

}
