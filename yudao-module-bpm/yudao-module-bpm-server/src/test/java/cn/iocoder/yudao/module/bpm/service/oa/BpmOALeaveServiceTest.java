package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.BpmFinanceAttachAccess;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOALeaveDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOALeaveMapper;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_LEAVE_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 请假详情读权：本人 / 当前办理人 / query 权限；待办不要求 bpm:oa-leave:query。
 */
class BpmOALeaveServiceTest {

    private static final String QUERY_PERMISSION = "bpm:oa-leave:query";

    private BpmOALeaveMapper leaveMapper;
    private SecurityFrameworkService securityFrameworkService;
    private ObjectProvider<TaskService> taskServiceProvider;
    private ObjectProvider<HistoryService> historyServiceProvider;
    private BpmOALeaveServiceImpl service;

    @BeforeEach
    void setUp() {
        leaveMapper = mock(BpmOALeaveMapper.class);
        securityFrameworkService = mock(SecurityFrameworkService.class);
        taskServiceProvider = mock(ObjectProvider.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(null);
        historyServiceProvider = mock(ObjectProvider.class);
        when(historyServiceProvider.getIfAvailable()).thenReturn(null);

        OaBillAccessPermission oaBillAccessPermission = new OaBillAccessPermission();
        ReflectionTestUtils.setField(oaBillAccessPermission, "taskServiceProvider", taskServiceProvider);
        ReflectionTestUtils.setField(oaBillAccessPermission, "historyServiceProvider", historyServiceProvider);
        ObjectProvider<BpmFinanceAttachAccess> attachProvider = mock(ObjectProvider.class);
        when(attachProvider.getIfAvailable()).thenReturn(null);
        ReflectionTestUtils.setField(oaBillAccessPermission, "financeAttachAccessProvider", attachProvider);

        service = new BpmOALeaveServiceImpl();
        ReflectionTestUtils.setField(service, "leaveMapper", leaveMapper);
        ReflectionTestUtils.setField(service, "securityFrameworkService", securityFrameworkService);
        ReflectionTestUtils.setField(service, "oaBillAccessPermission", oaBillAccessPermission);
    }

    @Test
    void ownerGetWithoutQuerySucceeds() {
        when(leaveMapper.selectById(10L)).thenReturn(ownedLeave(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        BpmOALeaveDO leave = service.getLeave(10L, 1L);
        assertEquals(10L, leave.getId());
        verifyNoInteractions(taskServiceProvider);
    }

    @Test
    void strangerGetWithoutQueryOrTaskIsAccessDenied() {
        when(leaveMapper.selectById(10L)).thenReturn(ownedLeave(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getLeave(10L, 9L));
        assertEquals(OA_LEAVE_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void activeAssigneeGetWithoutQuerySucceeds() {
        when(leaveMapper.selectById(10L)).thenReturn(ownedLeave(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        stubActiveAssignee("proc-1", 8L, 1L);

        BpmOALeaveDO leave = service.getLeave(10L, 8L);
        assertEquals(10L, leave.getId());
    }

    @Test
    void historicAssigneeGetWithoutQuerySucceeds() {
        when(leaveMapper.selectById(10L)).thenReturn(ownedLeave(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        stubActiveAssignee("proc-1", 8L, 0L);
        stubHistoricAssignee("proc-1", 8L, 1L);

        BpmOALeaveDO leave = service.getLeave(10L, 8L);
        assertEquals(10L, leave.getId());
    }

    @Test
    void getMappingDoesNotRequireQueryPermission() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/oa/BpmOALeaveController.java"));
        int getIdx = src.indexOf("@GetMapping(\"/get\")");
        int pageIdx = src.indexOf("@GetMapping(\"/page\")");
        assertTrue(getIdx >= 0 && pageIdx > getIdx);
        String getBlock = src.substring(getIdx, pageIdx);
        assertFalse(getBlock.contains("bpm:oa-leave:query"),
                "待办详情不得要求请假查询菜单权限");
    }

    private static BpmOALeaveDO ownedLeave(Long id, Long userId, String processInstanceId) {
        return BpmOALeaveDO.builder()
                .id(id)
                .userId(userId)
                .processInstanceId(processInstanceId)
                .build();
    }

    private void stubHistoricAssignee(String processInstanceId, Long userId, long count) {
        HistoryService historyService = mock(HistoryService.class);
        HistoricTaskInstanceQuery query = mock(HistoricTaskInstanceQuery.class);
        when(historyServiceProvider.getIfAvailable()).thenReturn(historyService);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(query);
        when(query.processInstanceId(processInstanceId)).thenReturn(query);
        when(query.taskAssignee(String.valueOf(userId))).thenReturn(query);
        when(query.count()).thenReturn(count);
    }

    private void stubActiveAssignee(String processInstanceId, Long userId, long count) {
        TaskService taskService = mock(TaskService.class);
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(taskService);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(processInstanceId)).thenReturn(taskQuery);
        when(taskQuery.taskCandidateOrAssigned(String.valueOf(userId))).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(count);
    }

}
