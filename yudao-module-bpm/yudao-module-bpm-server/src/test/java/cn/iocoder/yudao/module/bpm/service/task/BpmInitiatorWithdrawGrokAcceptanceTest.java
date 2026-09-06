package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static cn.iocoder.yudao.module.bpm.enums.BpmInitiatorWithdrawErrorCodeConstants.INITIATOR_WITHDRAW_CONCURRENT_CHANGE;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_DEFINITION_NOT_EXISTS;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.TASK_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Grok phase2a local-whitebox NEW cases. Does not modify product assertions. */
class BpmInitiatorWithdrawGrokAcceptanceTest {

    @Test
    void cfg02bStrCoercesNumericStringToIntegerOne() {
        BpmModelSaveReqVO parsed = JsonUtils.parseObject("{\"initiatorWithdrawMode\":\"1\"}", BpmModelSaveReqVO.class);
        assertNotNull(parsed);
        assertEquals(1, parsed.getInitiatorWithdrawMode());
    }

    @Test
    void cfg02bNoncoercibleBooleanAndArrayFailToParse() {
        assertThrows(RuntimeException.class,
                () -> JsonUtils.parseObject("{\"initiatorWithdrawMode\":true}", BpmModelSaveReqVO.class));
        assertThrows(RuntimeException.class,
                () -> JsonUtils.parseObject("{\"initiatorWithdrawMode\":[]}", BpmModelSaveReqVO.class));
    }

    @Test
    void pol02DirtyModesResolveToDisabled() {
        for (int dirty : new int[]{3, -1, 99}) {
            assertEquals(0, BpmInitiatorWithdrawPolicyService.resolveMode(
                    new BpmProcessDefinitionInfoDO().setInitiatorWithdrawMode(dirty).setAllowWithdrawTask(true)));
        }
    }

    @Test
    void pol03NullDefinition() {
        ServiceException ex = assertThrows(ServiceException.class, () -> BpmInitiatorWithdrawPolicyService.resolveMode(null));
        assertEquals(PROCESS_DEFINITION_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void humNoScopeMarkIsSilentNoSql() {
        BpmInitiatorWithdrawPolicyService service = new BpmInitiatorWithdrawPolicyService();
        Task task = mock(Task.class);
        when(task.getTaskDefinitionKey()).thenReturn("review");
        assertDoesNotThrow(() -> service.markHumanResult(task));
    }

    @Test
    void ten02MissingTenantDenied() {
        BpmInitiatorWithdrawPolicyService service = new BpmInitiatorWithdrawPolicyService();
        TenantContextHolder.clear();
        ServiceException ex = assertThrows(ServiceException.class, () -> service.withInstanceLock("x", () -> null));
        assertEquals(PROCESS_INSTANCE_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void ten07GuardRequiresCallerTransaction() {
        BpmInitiatorWithdrawPolicyService service = new BpmInitiatorWithdrawPolicyService();
        TenantContextHolder.setTenantId(1L);
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.withInstanceLock("x", () -> null));
            assertTrue(ex.getMessage().contains("requires the caller transaction"));
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void ret02bNonConflictServiceExceptionIsNotRetried() {
        BpmInitiatorWithdrawPolicyService service = new BpmInitiatorWithdrawPolicyService();
        BpmInitiatorWithdrawPolicyService transactional = mock(BpmInitiatorWithdrawPolicyService.class);
        Runnable action = () -> fail("must not run");
        try (MockedStatic<cn.hutool.extra.spring.SpringUtil> beans = mockStatic(cn.hutool.extra.spring.SpringUtil.class)) {
            beans.when(() -> cn.hutool.extra.spring.SpringUtil.getBean(BpmInitiatorWithdrawPolicyService.class)).thenReturn(transactional);
            ServiceException other = cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(TASK_NOT_EXISTS);
            doThrow(other).when(transactional).runAfterCompletionInTransaction("instance", action);
            assertSame(other, assertThrows(ServiceException.class,
                    () -> service.runAfterCompletion("instance", "1", "task", action)));
            verify(transactional, times(1)).runAfterCompletionInTransaction("instance", action);
            ServiceException conflict = cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(INITIATOR_WITHDRAW_CONCURRENT_CHANGE);
            assertNotEquals(conflict.getCode(), other.getCode());
        }
    }
}
