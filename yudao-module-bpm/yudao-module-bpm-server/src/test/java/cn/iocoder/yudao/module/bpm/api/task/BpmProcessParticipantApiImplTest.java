package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmProcessParticipantApiImplTest {

    private BpmProcessParticipantApiImpl api;
    private TaskQuery taskQuery;
    private HistoricTaskInstanceQuery historicQuery;

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(681L).setTenantId(1L).setUserType(2),
                new org.springframework.mock.web.MockHttpServletRequest());
        TaskService taskService = mock(TaskService.class);
        HistoryService historyService = mock(HistoryService.class);
        taskQuery = mock(TaskQuery.class);
        historicQuery = mock(HistoricTaskInstanceQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
        when(taskQuery.taskCandidateOrAssigned("681")).thenReturn(taskQuery);
        when(taskQuery.taskOwner("681")).thenReturn(taskQuery);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.processInstanceId("pi-1")).thenReturn(historicQuery);
        when(historicQuery.taskAssignee("681")).thenReturn(historicQuery);
        when(historicQuery.taskOwner("681")).thenReturn(historicQuery);

        ObjectProvider<TaskService> taskProvider = mock(ObjectProvider.class);
        ObjectProvider<HistoryService> historyProvider = mock(ObjectProvider.class);
        ObjectProvider<BpmFinanceAttachAccess> attachProvider = mock(ObjectProvider.class);
        when(taskProvider.getIfAvailable()).thenReturn(taskService);
        when(historyProvider.getIfAvailable()).thenReturn(historyService);
        when(attachProvider.getIfAvailable()).thenReturn(null);

        OaBillAccessPermission access = new OaBillAccessPermission();
        ReflectionTestUtils.setField(access, "taskServiceProvider", taskProvider);
        ReflectionTestUtils.setField(access, "historyServiceProvider", historyProvider);
        ReflectionTestUtils.setField(access, "financeAttachAccessProvider", attachProvider);

        api = new BpmProcessParticipantApiImpl();
        ReflectionTestUtils.setField(api, "oaBillAccessPermission", access);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void historicAssigneeIsReader() {
        when(taskQuery.count()).thenReturn(0L);
        when(historicQuery.count()).thenReturn(1L);
        assertTrue(api.canReadProcess("pi-1").getCheckedData());
    }

    @Test
    void activeCandidateIsReader() {
        when(taskQuery.count()).thenReturn(1L);
        when(historicQuery.count()).thenReturn(0L);
        assertTrue(api.canReadProcess("pi-1").getCheckedData());
    }

    @Test
    void strangerIsNotReader() {
        when(taskQuery.count()).thenReturn(0L);
        when(historicQuery.count()).thenReturn(0L);
        assertFalse(api.canReadProcess("pi-1").getCheckedData());
    }

    @Test
    void blankProcessIdIsNotReader() {
        assertFalse(api.canReadProcess("").getCheckedData());
        assertFalse(api.canReadProcess(null).getCheckedData());
    }
}
