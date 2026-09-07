package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceInfo;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOvertimeMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAQuotaLockMapper;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaAttendanceBusinessStartHolder;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import cn.iocoder.yudao.module.bpm.service.oa.listener.BpmOAOvertimeStatusListener;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_CALENDAR_MISSING;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_DAY_QUOTA_EXCEEDED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_DAY_TYPE_MISMATCH;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_TOO_SHORT;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_WORKDAY_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * U2 加班后端：创建启流、日额度、重叠、驳回不计入、读权、并发 5+5。
 */
class BpmOAOvertimeServiceTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 9, 6, 10, 0, 0);
    private static final String QUERY_PERMISSION = "bpm:oa-overtime:query";

    private BpmOAOvertimeMapper overtimeMapper;
    private BpmProcessInstanceApi processInstanceApi;
    private SecurityFrameworkService securityFrameworkService;
    private ObjectProvider<TaskService> taskServiceProvider;
    private OaBillAccessPermission oaBillAccessPermission;
    private BpmOAOvertimeServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        overtimeMapper = mock(BpmOAOvertimeMapper.class);
        processInstanceApi = mock(BpmProcessInstanceApi.class);
        securityFrameworkService = mock(SecurityFrameworkService.class);
        taskServiceProvider = mock(ObjectProvider.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(null);

        oaBillAccessPermission = new OaBillAccessPermission();
        ReflectionTestUtils.setField(oaBillAccessPermission, "taskServiceProvider", taskServiceProvider);

        service = new BpmOAOvertimeServiceImpl();
        ReflectionTestUtils.setField(service, "overtimeMapper", overtimeMapper);
        ReflectionTestUtils.setField(service, "quotaLockMapper", mock(BpmOAQuotaLockMapper.class));
        ReflectionTestUtils.setField(service, "processInstanceApi", processInstanceApi);
        ReflectionTestUtils.setField(service, "securityFrameworkService", securityFrameworkService);
        ReflectionTestUtils.setField(service, "oaBillAccessPermission", oaBillAccessPermission);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void createOvertime_validTwoHours_startsProcessWithHolidayFalse() {
        stubInsert(5L);
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any())).thenReturn(List.of());
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenAnswer(invocation -> {
                    assertTrue(OaAttendanceBusinessStartHolder.isSet());
                    return CommonResult.success("pi-ot-1");
                });

        Long id = service.createOvertime(1L, validCreateReq(DAY, DAY.plusHours(2), "false"));
        assertEquals(5L, id);

        ArgumentCaptor<BpmOAOvertimeDO> insertCaptor = ArgumentCaptor.forClass(BpmOAOvertimeDO.class);
        verify(overtimeMapper).insert(insertCaptor.capture());
        BpmOAOvertimeDO inserted = insertCaptor.getValue();
        assertEquals(1L, inserted.getUserId());
        assertEquals("项目上线", inserted.getReason());
        assertEquals("false", inserted.getHoliday());
        assertEquals(0, new BigDecimal("2.0").compareTo(inserted.getHours()));
        assertEquals(BpmTaskStatusEnum.RUNNING.getStatus(), inserted.getStatus());
        assertEquals(OaAttendanceSyncStatusEnum.NOT_SYNCED.getStatus(), inserted.getAttendanceSyncStatus());
        assertEquals(List.of("https://file.example/ot.png"), inserted.getAttachmentUrls());

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> dtoCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(1L), dtoCaptor.capture());
        BpmProcessInstanceCreateReqDTO dto = dtoCaptor.getValue();
        assertEquals(BpmOAOvertimeServiceImpl.PROCESS_KEY, dto.getProcessDefinitionKey());
        assertEquals("oa_overtime", dto.getProcessDefinitionKey());
        assertEquals("5", dto.getBusinessKey());
        assertEquals(0, new BigDecimal("2.0").compareTo((BigDecimal) dto.getVariables().get("hours")));
        assertEquals(Boolean.FALSE, dto.getVariables().get("holiday"));
        assertNull(dto.getStartUserSelectAssignees());
        assertNull(dto.getVariables().get("startCompanyDeptId"));

        ArgumentCaptor<BpmOAOvertimeDO> updateCaptor = ArgumentCaptor.forClass(BpmOAOvertimeDO.class);
        verify(overtimeMapper).updateById(updateCaptor.capture());
        assertEquals(5L, updateCaptor.getValue().getId());
        assertEquals("pi-ot-1", updateCaptor.getValue().getProcessInstanceId());
    }

    @Test
    void createOvertime_optionalStartCompanyDeptId_isProcessVariable() {
        stubInsert(8L);
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any())).thenReturn(List.of());
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-ot-8"));

        LocalDateTime legal = LocalDateTime.of(2026, 10, 1, 10, 0, 0);
        BpmOAOvertimeCreateReqVO req = validCreateReq(legal, legal.plusHours(2), "true");
        req.setStartCompanyDeptId(88L);
        service.createOvertime(1L, req);

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> dtoCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(1L), dtoCaptor.capture());
        assertEquals(Boolean.TRUE, dtoCaptor.getValue().getVariables().get("holiday"));
        assertEquals(88L, dtoCaptor.getValue().getVariables().get("startCompanyDeptId"));
    }

    @Test
    void createOvertime_dayQuotaWouldExceed8_rejected() {
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any()))
                .thenReturn(List.of(quotaRow(DAY, DAY.plusHours(6), new BigDecimal("6.0"),
                        BpmTaskStatusEnum.RUNNING.getStatus())));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(DAY.plusHours(6), DAY.plusHours(9), "false")));
        assertEquals(OA_OVERTIME_DAY_QUOTA_EXCEEDED.getCode(), ex.getCode());
        verify(overtimeMapper, never()).insert(any(BpmOAOvertimeDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createOvertime_overlap_rejected() {
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any()))
                .thenReturn(List.of(quotaRow(DAY, DAY.plusHours(4), new BigDecimal("4.0"),
                        BpmTaskStatusEnum.APPROVE.getStatus())));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(DAY.plusHours(2), DAY.plusHours(4), "false")));
        assertNotEquals(OA_OVERTIME_DAY_QUOTA_EXCEEDED.getCode(), ex.getCode());
        verify(overtimeMapper, never()).insert(any(BpmOAOvertimeDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createOvertime_rejectedHoursDoNotOccupyQuota() {
        stubInsert(9L);
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any()))
                .thenReturn(List.of(quotaRow(DAY, DAY.plusHours(6), new BigDecimal("6.0"),
                        BpmTaskStatusEnum.REJECT.getStatus())));
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-ot-9"));

        Long id = service.createOvertime(1L, validCreateReq(DAY, DAY.plusHours(3), "false"));
        assertEquals(9L, id);
        verify(overtimeMapper).insert(any(BpmOAOvertimeDO.class));
    }

    @Test
    void createOvertime_crossMidnightOnePlusOneWeekend_accepted() {
        stubInsert(21L);
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any())).thenReturn(List.of());
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-ot-21"));
        LocalDateTime start = LocalDateTime.of(2026, 9, 5, 23, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 6, 1, 0, 0);

        Long id = service.createOvertime(1L, validCreateReq(start, end, "false"));
        assertEquals(21L, id);
        ArgumentCaptor<BpmOAOvertimeDO> insertCaptor = ArgumentCaptor.forClass(BpmOAOvertimeDO.class);
        verify(overtimeMapper).insert(insertCaptor.capture());
        assertEquals(0, new BigDecimal("2.0").compareTo(insertCaptor.getValue().getHours()));
    }

    @Test
    void createOvertime_screenshotCrossMultiDay_rejectsWorkdaysNotTooShort() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 6, 9, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 8, 19, 0, 0);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(start, end, "false")));
        assertEquals(OA_OVERTIME_WORKDAY_FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("2026-09-07"));
        assertTrue(ex.getMessage().contains("2026-09-08"));
        assertNotEquals(OA_OVERTIME_TOO_SHORT.getCode(), ex.getCode());
        verify(overtimeMapper, never()).insert(any(BpmOAOvertimeDO.class));
    }

    @Test
    void createOvertime_weekday_rejected() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 7, 10, 0, 0);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(start, start.plusHours(2), "false")));
        assertEquals(OA_OVERTIME_WORKDAY_FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("2026-09-07"));
    }

    @Test
    void createOvertime_makeupRest_rejected() {
        LocalDateTime start = LocalDateTime.of(2026, 2, 20, 10, 0, 0);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(start, start.plusHours(2), "false")));
        assertEquals(OA_OVERTIME_WORKDAY_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void createOvertime_weekendWithHolidayTrue_typeMismatch() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(DAY, DAY.plusHours(2), "true")));
        assertEquals(OA_OVERTIME_DAY_TYPE_MISMATCH.getCode(), ex.getCode());
        verify(overtimeMapper, never()).insert(any(BpmOAOvertimeDO.class));
    }

    @Test
    void createOvertime_missingYear_rejected() {
        LocalDateTime start = LocalDateTime.of(2027, 1, 1, 10, 0, 0);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(start, start.plusHours(2), "true")));
        assertEquals(OA_OVERTIME_CALENDAR_MISSING.getCode(), ex.getCode());
        assertEquals("该年度节假日日历尚未发布或配置，请联系人事", ex.getMessage());
    }

    @Test
    void createOvertime_crossDayExistingRowOccupiesNextDayQuota() {
        LocalDateTime existingStart = LocalDateTime.of(2026, 9, 5, 23, 0, 0);
        LocalDateTime existingEnd = LocalDateTime.of(2026, 9, 6, 1, 0, 0);
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any()))
                .thenReturn(List.of(quotaRow(existingStart, existingEnd, new BigDecimal("2.0"),
                        BpmTaskStatusEnum.RUNNING.getStatus())));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(DAY, DAY.plusHours(8), "false")));
        assertEquals(OA_OVERTIME_DAY_QUOTA_EXCEEDED.getCode(), ex.getCode());
        verify(overtimeMapper, never()).insert(any(BpmOAOvertimeDO.class));
    }

    @Test
    void createOvertime_tooShort_rejected() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createOvertime(1L, validCreateReq(DAY, DAY.plusHours(1), "false")));
        assertEquals(OA_OVERTIME_TOO_SHORT.getCode(), ex.getCode());
        verify(overtimeMapper, never()).insert(any(BpmOAOvertimeDO.class));
    }

    @Test
    void createOvertime_blankReasonOrHoliday_rejected() {
        BpmOAOvertimeCreateReqVO blankReason = validCreateReq(DAY, DAY.plusHours(2), "false");
        blankReason.setReason("  ");
        assertThrows(ServiceException.class, () -> service.createOvertime(1L, blankReason));

        BpmOAOvertimeCreateReqVO blankHoliday = validCreateReq(DAY, DAY.plusHours(2), "false");
        blankHoliday.setHoliday("");
        assertThrows(ServiceException.class, () -> service.createOvertime(1L, blankHoliday));

        verify(overtimeMapper, never()).insert(any(BpmOAOvertimeDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createReqVoMustNotBindServerOwnedFields() {
        Set<String> fields = Arrays.stream(BpmOAOvertimeCreateReqVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fields.contains("hours"));
        assertFalse(fields.contains("userId"));
        assertFalse(fields.contains("status"));
        assertFalse(fields.contains("processInstanceId"));
        assertFalse(fields.contains("attendanceSyncStatus"));
        assertTrue(fields.contains("reason"));
        assertTrue(fields.contains("startTime"));
        assertTrue(fields.contains("endTime"));
        assertTrue(fields.contains("holiday"));
        assertTrue(fields.contains("attachmentUrls"));
        assertTrue(fields.contains("startCompanyDeptId"));
    }

    @Test
    void listenerWritesApprovedRejectedAndCancelStatus() {
        BpmOAOvertimeService overtimeService = mock(BpmOAOvertimeService.class);
        BpmOAOvertimeStatusListener listener = new BpmOAOvertimeStatusListener();
        ReflectionTestUtils.setField(listener, "overtimeService", overtimeService);

        listener.onApplicationEvent(statusEvent("oa_overtime", "11", BpmTaskStatusEnum.APPROVE.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_overtime", "12", BpmTaskStatusEnum.REJECT.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_overtime", "13", BpmTaskStatusEnum.CANCEL.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_outing", "14", BpmTaskStatusEnum.APPROVE.getStatus()));

        verify(overtimeService).updateOvertimeStatus(11L, BpmTaskStatusEnum.APPROVE.getStatus(), "pi-11");
        verify(overtimeService).updateOvertimeStatus(12L, BpmTaskStatusEnum.REJECT.getStatus(), "pi-12");
        verify(overtimeService).updateOvertimeStatus(13L, BpmTaskStatusEnum.CANCEL.getStatus(), "pi-13");
        verify(overtimeService, never()).updateOvertimeStatus(eq(14L), any(), any());
    }

    @Test
    void createReconcilesImmediateNonRunningStatus() {
        stubInsert(5L);
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any())).thenReturn(List.of());
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-ot-1"));
        ObjectProvider<org.flowable.engine.RuntimeService> runtimeProvider = mock(ObjectProvider.class);
        org.flowable.engine.RuntimeService runtime = mock(org.flowable.engine.RuntimeService.class);
        org.flowable.engine.runtime.ProcessInstanceQuery query =
                mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
        org.flowable.engine.runtime.ProcessInstance running =
                mock(org.flowable.engine.runtime.ProcessInstance.class);
        when(runtimeProvider.getIfAvailable()).thenReturn(runtime);
        when(runtime.createProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("pi-ot-1")).thenReturn(query);
        when(query.includeProcessVariables()).thenReturn(query);
        when(query.singleResult()).thenReturn(running);
        when(running.getProcessVariables()).thenReturn(Map.of(
                cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE.getStatus()));
        when(overtimeMapper.selectById(5L)).thenReturn(ownedOvertime(5L, 1L, "pi-ot-1"));
        ReflectionTestUtils.setField(service, "runtimeServiceProvider", runtimeProvider);

        service.createOvertime(1L, validCreateReq(DAY, DAY.plusHours(2), "false"));

        ArgumentCaptor<BpmOAOvertimeDO> captor = ArgumentCaptor.forClass(BpmOAOvertimeDO.class);
        verify(overtimeMapper, org.mockito.Mockito.atLeast(2)).updateById(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(row ->
                java.util.Objects.equals(row.getStatus(),
                        cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE.getStatus())));
    }

    @Test
    void boundStatusIgnoresMismatchedProcessInstanceId() {
        when(overtimeMapper.selectById(10L)).thenReturn(ownedOvertime(10L, 1L, "pi-A"));
        service.updateOvertimeStatus(10L, BpmTaskStatusEnum.APPROVE.getStatus(), "pi-B");
        verify(overtimeMapper, never()).updateById(any(BpmOAOvertimeDO.class));
    }

    @Test
    void boundStatusUpdatesWhenProcessInstanceIdMatches() {
        when(overtimeMapper.selectById(10L)).thenReturn(ownedOvertime(10L, 1L, "pi-A"));
        service.updateOvertimeStatus(10L, BpmTaskStatusEnum.APPROVE.getStatus(), "pi-A");
        ArgumentCaptor<BpmOAOvertimeDO> captor = ArgumentCaptor.forClass(BpmOAOvertimeDO.class);
        verify(overtimeMapper).updateById(captor.capture());
        assertEquals(BpmTaskStatusEnum.APPROVE.getStatus(), captor.getValue().getStatus());
    }

    @Test
    void pageWithoutQueryHidesOtherUsers() {
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        when(overtimeMapper.selectPage(eq(1L), any(BpmOAOvertimePageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.getOvertimePage(1L, new BpmOAOvertimePageReqVO());

        verify(overtimeMapper).selectPage(eq(1L), any(BpmOAOvertimePageReqVO.class));
        verify(overtimeMapper, never()).selectPage(isNull(), any(BpmOAOvertimePageReqVO.class));
    }

    @Test
    void ownerGetWithoutQuerySucceeds() {
        when(overtimeMapper.selectById(10L)).thenReturn(ownedOvertime(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        BpmOAOvertimeDO overtime = service.getOvertime(10L, 1L);
        assertEquals(10L, overtime.getId());
        verifyNoInteractions(taskServiceProvider);
    }

    @Test
    void strangerGetWithoutQueryOrTaskIsAccessDenied() {
        when(overtimeMapper.selectById(10L)).thenReturn(ownedOvertime(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getOvertime(10L, 9L));
        assertEquals(OA_OVERTIME_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void queryHolderGetWithoutOwnerOrTaskSucceeds() {
        when(overtimeMapper.selectById(10L)).thenReturn(ownedOvertime(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(true);

        BpmOAOvertimeDO overtime = service.getOvertime(10L, 9L);
        assertEquals(10L, overtime.getId());
        verifyNoInteractions(taskServiceProvider);
    }

    @Test
    void activeAssigneeGetWithoutQuerySucceeds() {
        when(overtimeMapper.selectById(10L)).thenReturn(ownedOvertime(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        stubActiveAssignee("proc-1", 8L, 1L);

        BpmOAOvertimeDO overtime = service.getOvertime(10L, 8L);
        assertEquals(10L, overtime.getId());
    }

    @Test
    void createOvertime_concurrentFivePlusFive_onlyOneSucceeds() throws Exception {
        ReentrantLock dayLock = new ReentrantLock();
        List<BpmOAOvertimeDO> store = new ArrayList<>();
        AtomicInteger idSeq = new AtomicInteger();
        when(overtimeMapper.selectByUserAndOverlapForUpdate(eq(1L), any(), any())).thenAnswer(invocation -> {
            dayLock.lock();
            return copyStore(store);
        });
        doAnswer(invocation -> {
            BpmOAOvertimeDO row = invocation.getArgument(0);
            row.setId((long) idSeq.incrementAndGet());
            store.add(copyRow(row));
            return 1;
        }).when(overtimeMapper).insert(any(BpmOAOvertimeDO.class));
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenAnswer(invocation -> CommonResult.success("pi-" + idSeq.get()));

        CyclicBarrier start = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                int offset = i * 5;
                pool.submit(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        BpmOAOvertimeCreateReqVO req = validCreateReq(
                                DAY.plusHours(offset), DAY.plusHours(offset + 5), "false");
                        service.createOvertime(1L, req);
                        successCount.incrementAndGet();
                    } catch (ServiceException ex) {
                        assertEquals(OA_OVERTIME_DAY_QUOTA_EXCEEDED.getCode(), ex.getCode());
                        failCount.incrementAndGet();
                    } catch (Exception ex) {
                        fail(ex);
                    } finally {
                        if (dayLock.isHeldByCurrentThread()) {
                            dayLock.unlock();
                        }
                        done.countDown();
                    }
                });
            }
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, successCount.get());
        assertEquals(1, failCount.get());
        verify(overtimeMapper, times(1)).insert(any(BpmOAOvertimeDO.class));
    }

    private void stubInsert(Long id) {
        doAnswer(invocation -> {
            BpmOAOvertimeDO overtime = invocation.getArgument(0);
            overtime.setId(id);
            return 1;
        }).when(overtimeMapper).insert(any(BpmOAOvertimeDO.class));
    }

    private static BpmOAOvertimeCreateReqVO validCreateReq(LocalDateTime start, LocalDateTime end, String holiday) {
        BpmOAOvertimeCreateReqVO req = new BpmOAOvertimeCreateReqVO();
        req.setReason("项目上线");
        req.setStartTime(start);
        req.setEndTime(end);
        req.setHoliday(holiday);
        req.setAttachmentUrls(List.of("https://file.example/ot.png"));
        return req;
    }

    private static BpmOAOvertimeDO quotaRow(LocalDateTime start, LocalDateTime end, BigDecimal hours, Integer status) {
        return BpmOAOvertimeDO.builder()
                .id(100L)
                .userId(1L)
                .startTime(start)
                .endTime(end)
                .hours(hours)
                .status(status)
                .build();
    }

    private static BpmOAOvertimeDO ownedOvertime(Long id, Long userId, String processInstanceId) {
        return BpmOAOvertimeDO.builder()
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
                .processInstanceId("pi-" + businessKey)
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

    private static List<BpmOAOvertimeDO> copyStore(List<BpmOAOvertimeDO> store) {
        List<BpmOAOvertimeDO> copy = new ArrayList<>();
        for (BpmOAOvertimeDO row : store) {
            copy.add(copyRow(row));
        }
        return copy;
    }

    private static BpmOAOvertimeDO copyRow(BpmOAOvertimeDO row) {
        return BpmOAOvertimeDO.builder()
                .id(row.getId())
                .userId(row.getUserId())
                .reason(row.getReason())
                .startTime(row.getStartTime())
                .endTime(row.getEndTime())
                .hours(row.getHours())
                .holiday(row.getHoliday())
                .status(row.getStatus())
                .processInstanceId(row.getProcessInstanceId())
                .build();
    }

}
