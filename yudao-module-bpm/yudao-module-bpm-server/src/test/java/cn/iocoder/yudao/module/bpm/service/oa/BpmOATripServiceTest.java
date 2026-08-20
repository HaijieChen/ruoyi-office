package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOATripCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOATripPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOATripDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOATripMapper;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import cn.iocoder.yudao.module.bpm.service.oa.listener.BpmOATripStatusListener;
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
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_TRIP_ACCESS_DENIED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * U3 出差后端：创建、列表数据范围、详情读权、状态监听。
 */
class BpmOATripServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 18, 9, 0, 0);
    private static final String QUERY_PERMISSION = "bpm:oa-trip:query";

    private BpmOATripMapper tripMapper;
    private BpmProcessInstanceApi processInstanceApi;
    private SecurityFrameworkService securityFrameworkService;
    private ObjectProvider<TaskService> taskServiceProvider;
    private OaBillAccessPermission oaBillAccessPermission;
    private BpmOATripServiceImpl service;

    @BeforeEach
    void setUp() {
        tripMapper = mock(BpmOATripMapper.class);
        processInstanceApi = mock(BpmProcessInstanceApi.class);
        securityFrameworkService = mock(SecurityFrameworkService.class);
        taskServiceProvider = mock(ObjectProvider.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(null);

        oaBillAccessPermission = new OaBillAccessPermission();
        ReflectionTestUtils.setField(oaBillAccessPermission, "taskServiceProvider", taskServiceProvider);

        cn.iocoder.yudao.module.system.api.user.AdminUserApi adminUserApi =
                mock(cn.iocoder.yudao.module.system.api.user.AdminUserApi.class);
        cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO companion =
                new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO();
        companion.setId(2L);
        companion.setNickname("李四");
        when(adminUserApi.getUser(2L)).thenReturn(CommonResult.success(companion));
        service = new BpmOATripServiceImpl();
        ReflectionTestUtils.setField(service, "tripMapper", tripMapper);
        ReflectionTestUtils.setField(service, "processInstanceApi", processInstanceApi);
        ReflectionTestUtils.setField(service, "securityFrameworkService", securityFrameworkService);
        ReflectionTestUtils.setField(service, "oaBillAccessPermission", oaBillAccessPermission);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
    }

    @Test
    void createTrip_startsProcessWithHoursAndType_andPersistsNotSynced() {
        doAnswer(invocation -> {
            BpmOATripDO trip = invocation.getArgument(0);
            trip.setId(5L);
            return 1;
        }).when(tripMapper).insert(any(BpmOATripDO.class));
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-1"));

        Long id = service.createTrip(1L, validCreateReq());
        assertEquals(5L, id);

        ArgumentCaptor<BpmOATripDO> insertCaptor = ArgumentCaptor.forClass(BpmOATripDO.class);
        verify(tripMapper).insert(insertCaptor.capture());
        BpmOATripDO inserted = insertCaptor.getValue();
        assertEquals(1L, inserted.getUserId());
        assertEquals("北京", inserted.getDestination());
        assertEquals("客户拜访", inserted.getReason());
        assertEquals(2L, inserted.getCompanionUserId());
        assertEquals(0, new BigDecimal("1.5").compareTo(inserted.getHours()));
        assertEquals(BpmTaskStatusEnum.RUNNING.getStatus(), inserted.getStatus());
        assertEquals(OaAttendanceSyncStatusEnum.NOT_SYNCED.getStatus(), inserted.getAttendanceSyncStatus());

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> dtoCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(1L), dtoCaptor.capture());
        BpmProcessInstanceCreateReqDTO dto = dtoCaptor.getValue();
        assertEquals(BpmOATripServiceImpl.PROCESS_KEY, dto.getProcessDefinitionKey());
        assertEquals("oa_business_trip", dto.getProcessDefinitionKey());
        assertEquals("5", dto.getBusinessKey());
        assertEquals("北京", dto.getVariables().get("destination"));
        assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) dto.getVariables().get("hours")));
        assertNull(dto.getStartUserSelectAssignees());

        ArgumentCaptor<BpmOATripDO> updateCaptor = ArgumentCaptor.forClass(BpmOATripDO.class);
        verify(tripMapper).updateById(updateCaptor.capture());
        assertEquals(5L, updateCaptor.getValue().getId());
        assertEquals("pi-1", updateCaptor.getValue().getProcessInstanceId());
    }

    @Test
    void createTrip_invalidRange_doesNotInsert() {
        BpmOATripCreateReqVO req = validCreateReq();
        req.setEndTime(START);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createTrip(1L, req));
        assertEquals(OA_DURATION_INVALID.getCode(), ex.getCode());
        verify(tripMapper, never()).insert(any(BpmOATripDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createTrip_ignoresStartUserSelectAssignees() {
        doAnswer(invocation -> {
            BpmOATripDO trip = invocation.getArgument(0);
            trip.setId(7L);
            return 1;
        }).when(tripMapper).insert(any(BpmOATripDO.class));
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-7"));

        BpmOATripCreateReqVO req = validCreateReq();
        req.setStartUserSelectAssignees(Map.of("deptLeader", List.of(99L)));
        service.createTrip(1L, req);

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> dtoCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(1L), dtoCaptor.capture());
        assertNull(dtoCaptor.getValue().getStartUserSelectAssignees());
    }

    @Test
    void createReqVoMustNotBindServerOwnedFields() {
        Set<String> fields = Arrays.stream(BpmOATripCreateReqVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fields.contains("hours"));
        assertFalse(fields.contains("userId"));
        assertFalse(fields.contains("status"));
        assertFalse(fields.contains("processInstanceId"));
        assertFalse(fields.contains("attendanceSyncStatus"));
        assertTrue(fields.contains("type"));
        assertTrue(fields.contains("startTime"));
        assertTrue(fields.contains("endTime"));
    }

    @Test
    void listenerWritesApprovedRejectedAndCancelStatus() {
        BpmOATripService tripService = mock(BpmOATripService.class);
        BpmOATripStatusListener listener = new BpmOATripStatusListener();
        ReflectionTestUtils.setField(listener, "tripService", tripService);

        listener.onApplicationEvent(statusEvent("oa_business_trip", "11", BpmTaskStatusEnum.APPROVE.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_business_trip", "12", BpmTaskStatusEnum.REJECT.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_business_trip", "13", BpmTaskStatusEnum.CANCEL.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_outing", "14", BpmTaskStatusEnum.APPROVE.getStatus()));

        verify(tripService).updateTripStatus(11L, BpmTaskStatusEnum.APPROVE.getStatus());
        verify(tripService).updateTripStatus(12L, BpmTaskStatusEnum.REJECT.getStatus());
        verify(tripService).updateTripStatus(13L, BpmTaskStatusEnum.CANCEL.getStatus());
        verify(tripService, never()).updateTripStatus(eq(14L), any());
    }

    @Test
    void pageWithoutQueryHidesOtherUsers() {
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        when(tripMapper.selectPage(eq(1L), any(BpmOATripPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.getTripPage(1L, new BpmOATripPageReqVO());

        verify(tripMapper).selectPage(eq(1L), any(BpmOATripPageReqVO.class));
        verify(tripMapper, never()).selectPage(isNull(Long.class), any(BpmOATripPageReqVO.class));
    }

    @Test
    void pageWithQueryPassesNullUserId() {
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(true);
        when(tripMapper.selectPage(isNull(), any(BpmOATripPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.getTripPage(1L, new BpmOATripPageReqVO());

        verify(tripMapper).selectPage(isNull(), any(BpmOATripPageReqVO.class));
    }

    @Test
    void ownerGetWithoutQuerySucceeds() {
        when(tripMapper.selectById(10L)).thenReturn(ownedTrip(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        BpmOATripDO trip = service.getTrip(10L, 1L);
        assertEquals(10L, trip.getId());
        verifyNoInteractions(taskServiceProvider);
    }

    @Test
    void strangerGetWithoutQueryOrTaskIsAccessDenied() {
        when(tripMapper.selectById(10L)).thenReturn(ownedTrip(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getTrip(10L, 9L));
        assertEquals(OA_TRIP_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void activeAssigneeGetWithoutQuerySucceeds() {
        when(tripMapper.selectById(10L)).thenReturn(ownedTrip(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        stubActiveAssignee("proc-1", 8L, 1L);

        BpmOATripDO trip = service.getTrip(10L, 8L);
        assertEquals(10L, trip.getId());
    }

    private static BpmOATripCreateReqVO validCreateReq() {
        BpmOATripCreateReqVO req = new BpmOATripCreateReqVO();
        req.setDestination("北京");
        req.setReason("客户拜访");
        req.setCompanionUserId(2L);
        req.setStartTime(START);
        req.setEndTime(START.plusMinutes(90));
        return req;
    }

    private static BpmOATripDO ownedTrip(Long id, Long userId, String processInstanceId) {
        return BpmOATripDO.builder()
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
