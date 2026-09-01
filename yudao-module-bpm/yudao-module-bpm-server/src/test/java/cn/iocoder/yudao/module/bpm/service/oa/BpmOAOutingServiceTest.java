package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmFinanceAttachAccess;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOutingDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOutingMapper;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import cn.iocoder.yudao.module.bpm.service.oa.listener.BpmOAOutingStatusListener;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_DURATION_INVALID;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OUTING_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * U4 外出后端：创建、列表数据范围、详情读权、状态监听。
 */
class BpmOAOutingServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 18, 9, 0, 0);
    private static final String QUERY_PERMISSION = "bpm:oa-outing:query";

    private BpmOAOutingMapper outingMapper;
    private BpmProcessInstanceApi processInstanceApi;
    private SecurityFrameworkService securityFrameworkService;
    private ObjectProvider<TaskService> taskServiceProvider;
    private OaBillAccessPermission oaBillAccessPermission;
    private BpmFinanceAttachAccess financeAttachAccess;
    private BpmOAOutingServiceImpl service;

    @BeforeEach
    void setUp() {
        outingMapper = mock(BpmOAOutingMapper.class);
        processInstanceApi = mock(BpmProcessInstanceApi.class);
        securityFrameworkService = mock(SecurityFrameworkService.class);
        taskServiceProvider = mock(ObjectProvider.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(null);

        oaBillAccessPermission = new OaBillAccessPermission();
        ReflectionTestUtils.setField(oaBillAccessPermission, "taskServiceProvider", taskServiceProvider);
        financeAttachAccess = mock(BpmFinanceAttachAccess.class);
        ObjectProvider<BpmFinanceAttachAccess> attachProvider = mock(ObjectProvider.class);
        when(attachProvider.getIfAvailable()).thenReturn(financeAttachAccess);
        ReflectionTestUtils.setField(oaBillAccessPermission, "financeAttachAccessProvider", attachProvider);

        service = new BpmOAOutingServiceImpl();
        ReflectionTestUtils.setField(service, "outingMapper", outingMapper);
        ReflectionTestUtils.setField(service, "processInstanceApi", processInstanceApi);
        ReflectionTestUtils.setField(service, "securityFrameworkService", securityFrameworkService);
        ReflectionTestUtils.setField(service, "oaBillAccessPermission", oaBillAccessPermission);
    }

    @Test
    void createOuting_startsProcessWithHoursAndNeedOutput_andPersistsAttachments() {
        doAnswer(invocation -> {
            BpmOAOutingDO outing = invocation.getArgument(0);
            outing.setId(5L);
            return 1;
        }).when(outingMapper).insert(any(BpmOAOutingDO.class));
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-1"));

        Long id = service.createOuting(1L, validCreateReq());
        assertEquals(5L, id);

        ArgumentCaptor<BpmOAOutingDO> insertCaptor = ArgumentCaptor.forClass(BpmOAOutingDO.class);
        verify(outingMapper).insert(insertCaptor.capture());
        BpmOAOutingDO inserted = insertCaptor.getValue();
        assertEquals(1L, inserted.getUserId());
        assertEquals("客户拜访", inserted.getReason());
        assertEquals("上海", inserted.getLocation());
        assertEquals("true", inserted.getNeedOutput());
        assertEquals(List.of("https://file.example/a.png"), inserted.getAttachmentUrls());
        assertEquals(0, new BigDecimal("1.5").compareTo(inserted.getHours()));
        assertEquals(BpmTaskStatusEnum.RUNNING.getStatus(), inserted.getStatus());
        assertEquals(OaAttendanceSyncStatusEnum.NOT_SYNCED.getStatus(), inserted.getAttendanceSyncStatus());

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> dtoCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(1L), dtoCaptor.capture());
        BpmProcessInstanceCreateReqDTO dto = dtoCaptor.getValue();
        assertEquals(BpmOAOutingServiceImpl.PROCESS_KEY, dto.getProcessDefinitionKey());
        assertEquals("oa_outing", dto.getProcessDefinitionKey());
        assertEquals("5", dto.getBusinessKey());
        assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) dto.getVariables().get("hours")));
        assertEquals("OUT-5", dto.getVariables().get("billCode"));
        assertEquals("true", dto.getVariables().get("need_output"));
        assertNull(dto.getStartUserSelectAssignees());

        ArgumentCaptor<BpmOAOutingDO> updateCaptor = ArgumentCaptor.forClass(BpmOAOutingDO.class);
        verify(outingMapper).updateById(updateCaptor.capture());
        assertEquals(5L, updateCaptor.getValue().getId());
        assertEquals("pi-1", updateCaptor.getValue().getProcessInstanceId());
    }

    @Test
    void createOuting_blankReasonOrLocation_rejected() {
        BpmOAOutingCreateReqVO blankReason = validCreateReq();
        blankReason.setReason("  ");
        ServiceException reasonEx = assertThrows(ServiceException.class, () -> service.createOuting(1L, blankReason));
        assertNotEquals(OA_DURATION_INVALID.getCode(), reasonEx.getCode());

        BpmOAOutingCreateReqVO blankLocation = validCreateReq();
        blankLocation.setLocation("");
        assertThrows(ServiceException.class, () -> service.createOuting(1L, blankLocation));

        verify(outingMapper, never()).insert(any(BpmOAOutingDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createOuting_invalidRange_doesNotInsert() {
        BpmOAOutingCreateReqVO req = validCreateReq();
        req.setEndTime(START);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createOuting(1L, req));
        assertEquals(OA_DURATION_INVALID.getCode(), ex.getCode());
        verify(outingMapper, never()).insert(any(BpmOAOutingDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createOuting_ignoresStartUserSelectAssignees() {
        doAnswer(invocation -> {
            BpmOAOutingDO outing = invocation.getArgument(0);
            outing.setId(7L);
            return 1;
        }).when(outingMapper).insert(any(BpmOAOutingDO.class));
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-7"));

        BpmOAOutingCreateReqVO req = validCreateReq();
        req.setStartUserSelectAssignees(Map.of("deptLeader", List.of(99L)));
        service.createOuting(1L, req);

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> dtoCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(1L), dtoCaptor.capture());
        assertNull(dtoCaptor.getValue().getStartUserSelectAssignees());
    }

    @Test
    void createReqVoMustNotBindServerOwnedFields() {
        Set<String> fields = Arrays.stream(BpmOAOutingCreateReqVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fields.contains("hours"));
        assertFalse(fields.contains("userId"));
        assertFalse(fields.contains("status"));
        assertFalse(fields.contains("processInstanceId"));
        assertFalse(fields.contains("attendanceSyncStatus"));
        assertTrue(fields.contains("reason"));
        assertTrue(fields.contains("location"));
        assertTrue(fields.contains("startTime"));
        assertTrue(fields.contains("endTime"));
        assertTrue(fields.contains("needOutput"));
        assertTrue(fields.contains("attachmentUrls"));
    }

    @Test
    void listenerWritesApprovedRejectedAndCancelStatus() {
        BpmOAOutingService outingService = mock(BpmOAOutingService.class);
        BpmOAOutingStatusListener listener = new BpmOAOutingStatusListener();
        ReflectionTestUtils.setField(listener, "outingService", outingService);

        listener.onApplicationEvent(statusEvent("oa_outing", "11", BpmTaskStatusEnum.APPROVE.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_outing", "12", BpmTaskStatusEnum.REJECT.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_outing", "13", BpmTaskStatusEnum.CANCEL.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_business_trip", "14", BpmTaskStatusEnum.APPROVE.getStatus()));

        verify(outingService).updateOutingStatus(11L, BpmTaskStatusEnum.APPROVE.getStatus());
        verify(outingService).updateOutingStatus(12L, BpmTaskStatusEnum.REJECT.getStatus());
        verify(outingService).updateOutingStatus(13L, BpmTaskStatusEnum.CANCEL.getStatus());
        verify(outingService, never()).updateOutingStatus(eq(14L), any());
    }

    @Test
    void pageWithoutQueryHidesOtherUsers() {
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        when(outingMapper.selectPage(eq(1L), any(BpmOAOutingPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.getOutingPage(1L, new BpmOAOutingPageReqVO());

        verify(outingMapper).selectPage(eq(1L), any(BpmOAOutingPageReqVO.class));
        verify(outingMapper, never()).selectPage(isNull(), any(BpmOAOutingPageReqVO.class));
    }

    @Test
    void pageWithQueryPassesNullUserId() {
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(true);
        when(outingMapper.selectPage(isNull(), any(BpmOAOutingPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.getOutingPage(1L, new BpmOAOutingPageReqVO());

        verify(outingMapper).selectPage(isNull(), any(BpmOAOutingPageReqVO.class));
    }

    @Test
    void ownerGetWithoutQuerySucceeds() {
        when(outingMapper.selectById(10L)).thenReturn(ownedOuting(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        BpmOAOutingDO outing = service.getOuting(10L, 1L);
        assertEquals(10L, outing.getId());
        verifyNoInteractions(taskServiceProvider);
    }

    @Test
    void strangerGetWithoutQueryOrTaskIsAccessDenied() {
        when(outingMapper.selectById(10L)).thenReturn(ownedOuting(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getOuting(10L, 9L));
        assertEquals(OA_OUTING_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void queryHolderGetWithoutOwnerOrTaskSucceeds() {
        when(outingMapper.selectById(10L)).thenReturn(ownedOuting(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(true);

        BpmOAOutingDO outing = service.getOuting(10L, 9L);
        assertEquals(10L, outing.getId());
        verifyNoInteractions(taskServiceProvider);
    }

    @Test
    void activeAssigneeGetWithoutQuerySucceeds() {
        when(outingMapper.selectById(10L)).thenReturn(ownedOuting(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        stubActiveAssignee("proc-1", 8L, 1L);

        BpmOAOutingDO outing = service.getOuting(10L, 8L);
        assertEquals(10L, outing.getId());
    }

    @Test
    void attachingBillViewerGetWithoutNativeAclSucceeds() {
        when(outingMapper.selectById(10L)).thenReturn(ownedOuting(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        when(financeAttachAccess.canReadProcessInstanceViaBill(9L, "proc-1")).thenReturn(true);

        BpmOAOutingDO outing = service.getOuting(10L, 9L);
        assertEquals(10L, outing.getId());
    }

    private static BpmOAOutingCreateReqVO validCreateReq() {
        BpmOAOutingCreateReqVO req = new BpmOAOutingCreateReqVO();
        req.setReason("客户拜访");
        req.setLocation("上海");
        req.setStartTime(START);
        req.setEndTime(START.plusMinutes(90));
        req.setNeedOutput("true");
        req.setAttachmentUrls(List.of("https://file.example/a.png"));
        return req;
    }

    private static BpmOAOutingDO ownedOuting(Long id, Long userId, String processInstanceId) {
        return BpmOAOutingDO.builder()
                .id(id)
                .userId(userId)
                .processInstanceId(processInstanceId)
                .build();
    }

    private static BpmProcessInstanceStatusEvent statusEvent(String processKey, String businessKey, Integer status) {
        BpmProcessInstanceStatusEvent event = new BpmProcessInstanceStatusEvent();
        event.setProcessInstanceInfo(BpmProcessInstanceInfo.builder()
                .processDefinitionKey(processKey)
                .businessKey(businessKey)
                .status(status)
                .build());
        return event;
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
