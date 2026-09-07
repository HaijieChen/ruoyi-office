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
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAPunchCorrectionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAPunchCorrectionMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAQuotaLockMapper;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import cn.iocoder.yudao.module.bpm.service.oa.listener.BpmOAPunchCorrectionStatusListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_PUNCH_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_PUNCH_MONTH_QUOTA_EXCEEDED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * U3 补卡后端：创建启流、月次数、按补卡日期所在月、驳回恢复、读权、并发第 2/3 次。
 */
class BpmOAPunchCorrectionServiceTest {

    private static final LocalDate AUG_15 = LocalDate.of(2026, 8, 15);
    private static final LocalDate AUG_20 = LocalDate.of(2026, 8, 20);
    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDateTime PUNCH_TIME = LocalDateTime.of(2026, 8, 15, 9, 0, 0);
    private static final String QUERY_PERMISSION = "bpm:oa-punch-correction:query";

    private BpmOAPunchCorrectionMapper punchCorrectionMapper;
    private BpmProcessInstanceApi processInstanceApi;
    private SecurityFrameworkService securityFrameworkService;
    private ObjectProvider<TaskService> taskServiceProvider;
    private ObjectProvider<HistoryService> historyServiceProvider;
    private OaBillAccessPermission oaBillAccessPermission;
    private BpmOAPunchCorrectionServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        punchCorrectionMapper = mock(BpmOAPunchCorrectionMapper.class);
        processInstanceApi = mock(BpmProcessInstanceApi.class);
        securityFrameworkService = mock(SecurityFrameworkService.class);
        taskServiceProvider = mock(ObjectProvider.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(null);
        historyServiceProvider = mock(ObjectProvider.class);
        when(historyServiceProvider.getIfAvailable()).thenReturn(null);

        oaBillAccessPermission = new OaBillAccessPermission();
        ReflectionTestUtils.setField(oaBillAccessPermission, "taskServiceProvider", taskServiceProvider);
        ReflectionTestUtils.setField(oaBillAccessPermission, "historyServiceProvider", historyServiceProvider);

        service = new BpmOAPunchCorrectionServiceImpl();
        ReflectionTestUtils.setField(service, "punchCorrectionMapper", punchCorrectionMapper);
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
    void remainingTwo_createSucceedsAndRemainingBecomesOne() {
        when(punchCorrectionMapper.selectByUserAndMonth(eq(1L), any(), any())).thenReturn(List.of());
        assertEquals(2, service.getRemainingCount(1L, AUG_15));

        stubInsert(5L);
        when(punchCorrectionMapper.selectByUserAndMonthForUpdate(eq(1L), any(), any())).thenReturn(List.of());
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenAnswer(invocation -> {
                    assertTrue(cn.iocoder.yudao.module.bpm.framework.security.OaAttendanceBusinessStartHolder.isSet());
                    return CommonResult.success("pi-pc-1");
                });

        Long id = service.createPunchCorrection(1L, validCreateReq(AUG_15, PUNCH_TIME));
        assertEquals(5L, id);

        ArgumentCaptor<BpmOAPunchCorrectionDO> insertCaptor = ArgumentCaptor.forClass(BpmOAPunchCorrectionDO.class);
        verify(punchCorrectionMapper).insert(insertCaptor.capture());
        BpmOAPunchCorrectionDO inserted = insertCaptor.getValue();
        assertEquals(1L, inserted.getUserId());
        assertEquals(AUG_15, inserted.getPunchDate());
        assertEquals(PUNCH_TIME, inserted.getPunchTime());
        assertEquals("忘记打卡", inserted.getReason());
        assertEquals(List.of("https://file.example/pc.png"), inserted.getAttachmentUrls());
        assertEquals(BpmTaskStatusEnum.RUNNING.getStatus(), inserted.getStatus());
        assertEquals(OaAttendanceSyncStatusEnum.NOT_SYNCED.getStatus(), inserted.getAttendanceSyncStatus());

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> dtoCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(1L), dtoCaptor.capture());
        BpmProcessInstanceCreateReqDTO dto = dtoCaptor.getValue();
        assertEquals(BpmOAPunchCorrectionServiceImpl.PROCESS_KEY, dto.getProcessDefinitionKey());
        assertEquals("oa_punch_correction", dto.getProcessDefinitionKey());
        assertEquals("5", dto.getBusinessKey());
        assertEquals("2026-08-15", dto.getVariables().get("punchDate"));
        assertNull(dto.getStartUserSelectAssignees());

        when(punchCorrectionMapper.selectByUserAndMonth(eq(1L), any(), any()))
                .thenReturn(List.of(quotaRow(AUG_15, BpmTaskStatusEnum.RUNNING.getStatus())));
        assertEquals(1, service.getRemainingCount(1L, AUG_15));
    }

    @Test
    void alreadyTwoInMonth_createRejected() {
        when(punchCorrectionMapper.selectByUserAndMonthForUpdate(eq(1L), any(), any()))
                .thenReturn(List.of(
                        quotaRow(AUG_15, BpmTaskStatusEnum.RUNNING.getStatus()),
                        quotaRow(AUG_20, BpmTaskStatusEnum.APPROVE.getStatus())));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createPunchCorrection(1L, validCreateReq(AUG_15, PUNCH_TIME)));
        assertEquals(OA_PUNCH_MONTH_QUOTA_EXCEEDED.getCode(), ex.getCode());
        verify(punchCorrectionMapper, never()).insert(any(BpmOAPunchCorrectionDO.class));
        verifyNoInteractions(processInstanceApi);

        when(punchCorrectionMapper.selectByUserAndMonth(eq(1L), any(), any()))
                .thenReturn(List.of(
                        quotaRow(AUG_15, BpmTaskStatusEnum.RUNNING.getStatus()),
                        quotaRow(AUG_20, BpmTaskStatusEnum.APPROVE.getStatus())));
        assertEquals(0, service.getRemainingCount(1L, AUG_15));
    }

    @Test
    void septemberSubmitAugustDate_occupiesAugustNotSeptember() {
        stubInsert(7L);
        when(punchCorrectionMapper.selectByUserAndMonthForUpdate(eq(1L),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 9, 1))))
                .thenReturn(List.of());
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-pc-7"));

        Long id = service.createPunchCorrection(1L, validCreateReq(AUG_15, PUNCH_TIME));
        assertEquals(7L, id);
        verify(punchCorrectionMapper).selectByUserAndMonthForUpdate(eq(1L),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 9, 1)));
        verify(punchCorrectionMapper, never()).selectByUserAndMonthForUpdate(eq(1L),
                eq(LocalDate.of(2026, 9, 1)), any());

        when(punchCorrectionMapper.selectByUserAndMonth(eq(1L),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 9, 1))))
                .thenReturn(List.of(quotaRow(AUG_15, BpmTaskStatusEnum.RUNNING.getStatus())));
        when(punchCorrectionMapper.selectByUserAndMonth(eq(1L),
                eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 10, 1))))
                .thenReturn(List.of());
        assertEquals(1, service.getRemainingCount(1L, AUG_15));
        assertEquals(2, service.getRemainingCount(1L, SEP_1));
    }

    @Test
    void rejectRestoresRemaining() {
        when(punchCorrectionMapper.selectById(11L)).thenReturn(ownedPunch(11L, 1L, "proc-11"));
        service.updatePunchCorrectionStatus(11L, BpmTaskStatusEnum.REJECT.getStatus());
        ArgumentCaptor<BpmOAPunchCorrectionDO> statusCaptor = ArgumentCaptor.forClass(BpmOAPunchCorrectionDO.class);
        verify(punchCorrectionMapper).updateById(statusCaptor.capture());
        assertEquals(11L, statusCaptor.getValue().getId());
        assertEquals(BpmTaskStatusEnum.REJECT.getStatus(), statusCaptor.getValue().getStatus());

        when(punchCorrectionMapper.selectByUserAndMonth(eq(1L), any(), any()))
                .thenReturn(List.of(
                        quotaRow(AUG_15, BpmTaskStatusEnum.REJECT.getStatus()),
                        quotaRow(AUG_20, BpmTaskStatusEnum.RUNNING.getStatus())));
        assertEquals(1, service.getRemainingCount(1L, AUG_15));
    }

    @Test
    void createPunchCorrection_optionalStartCompanyDeptId_isProcessVariable() {
        stubInsert(8L);
        when(punchCorrectionMapper.selectByUserAndMonthForUpdate(eq(1L), any(), any())).thenReturn(List.of());
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("pi-pc-8"));

        BpmOAPunchCorrectionCreateReqVO req = validCreateReq(AUG_15, PUNCH_TIME);
        req.setStartCompanyDeptId(88L);
        service.createPunchCorrection(1L, req);

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> dtoCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(1L), dtoCaptor.capture());
        assertEquals(88L, dtoCaptor.getValue().getVariables().get("startCompanyDeptId"));
    }

    @Test
    void createPunchCorrection_blankReason_rejected() {
        BpmOAPunchCorrectionCreateReqVO blankReason = validCreateReq(AUG_15, PUNCH_TIME);
        blankReason.setReason("  ");
        assertThrows(ServiceException.class, () -> service.createPunchCorrection(1L, blankReason));
        verify(punchCorrectionMapper, never()).insert(any(BpmOAPunchCorrectionDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createReqVoMustNotBindServerOwnedFields() {
        Set<String> fields = Arrays.stream(BpmOAPunchCorrectionCreateReqVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fields.contains("userId"));
        assertFalse(fields.contains("status"));
        assertFalse(fields.contains("processInstanceId"));
        assertFalse(fields.contains("attendanceSyncStatus"));
        assertTrue(fields.contains("punchDate"));
        assertTrue(fields.contains("punchTime"));
        assertTrue(fields.contains("reason"));
        assertTrue(fields.contains("attachmentUrls"));
        assertTrue(fields.contains("startCompanyDeptId"));
    }

    @Test
    void listenerWritesApprovedRejectedAndCancelStatus() {
        BpmOAPunchCorrectionService punchService = mock(BpmOAPunchCorrectionService.class);
        BpmOAPunchCorrectionStatusListener listener = new BpmOAPunchCorrectionStatusListener();
        ReflectionTestUtils.setField(listener, "punchCorrectionService", punchService);

        listener.onApplicationEvent(statusEvent("oa_punch_correction", "11", BpmTaskStatusEnum.APPROVE.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_punch_correction", "12", BpmTaskStatusEnum.REJECT.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_punch_correction", "13", BpmTaskStatusEnum.CANCEL.getStatus()));
        listener.onApplicationEvent(statusEvent("oa_overtime", "14", BpmTaskStatusEnum.APPROVE.getStatus()));

        verify(punchService).updatePunchCorrectionStatus(11L, BpmTaskStatusEnum.APPROVE.getStatus(), "pi-11");
        verify(punchService).updatePunchCorrectionStatus(12L, BpmTaskStatusEnum.REJECT.getStatus(), "pi-12");
        verify(punchService).updatePunchCorrectionStatus(13L, BpmTaskStatusEnum.CANCEL.getStatus(), "pi-13");
        verify(punchService, never()).updatePunchCorrectionStatus(eq(14L), any(), any());
    }

    @Test
    void boundStatusIgnoresMismatchedProcessInstanceId() {
        when(punchCorrectionMapper.selectById(10L)).thenReturn(ownedPunch(10L, 1L, "pi-A"));
        service.updatePunchCorrectionStatus(10L, BpmTaskStatusEnum.APPROVE.getStatus(), "pi-B");
        verify(punchCorrectionMapper, never()).updateById(any(BpmOAPunchCorrectionDO.class));
    }

    @Test
    void pageWithoutQueryHidesOtherUsers() {
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        when(punchCorrectionMapper.selectPage(eq(1L), any(BpmOAPunchCorrectionPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.getPunchCorrectionPage(1L, new BpmOAPunchCorrectionPageReqVO());

        verify(punchCorrectionMapper).selectPage(eq(1L), any(BpmOAPunchCorrectionPageReqVO.class));
        verify(punchCorrectionMapper, never()).selectPage(isNull(), any(BpmOAPunchCorrectionPageReqVO.class));
    }

    @Test
    void ownerGetWithoutQuerySucceeds() {
        when(punchCorrectionMapper.selectById(10L)).thenReturn(ownedPunch(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        BpmOAPunchCorrectionDO punch = service.getPunchCorrection(10L, 1L);
        assertEquals(10L, punch.getId());
        verifyNoInteractions(taskServiceProvider);
    }

    @Test
    void strangerGetWithoutQueryOrTaskIsAccessDenied() {
        when(punchCorrectionMapper.selectById(10L)).thenReturn(ownedPunch(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getPunchCorrection(10L, 9L));
        assertEquals(OA_PUNCH_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void queryHolderGetWithoutOwnerOrTaskSucceeds() {
        when(punchCorrectionMapper.selectById(10L)).thenReturn(ownedPunch(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(true);

        BpmOAPunchCorrectionDO punch = service.getPunchCorrection(10L, 9L);
        assertEquals(10L, punch.getId());
        verifyNoInteractions(taskServiceProvider);
    }

    @Test
    void activeAssigneeGetWithoutQuerySucceeds() {
        when(punchCorrectionMapper.selectById(10L)).thenReturn(ownedPunch(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        stubActiveAssignee("proc-1", 8L, 1L);

        BpmOAPunchCorrectionDO punch = service.getPunchCorrection(10L, 8L);
        assertEquals(10L, punch.getId());
    }

    @Test
    void finishedAssigneeGetWithoutQuerySucceeds() {
        when(punchCorrectionMapper.selectById(10L)).thenReturn(ownedPunch(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        stubHistoricAssignee("proc-1", 8L, 1L);

        BpmOAPunchCorrectionDO punch = service.getPunchCorrection(10L, 8L);
        assertEquals(10L, punch.getId());
    }

    @Test
    void strangerGetAfterFinishIsAccessDenied() {
        when(punchCorrectionMapper.selectById(10L)).thenReturn(ownedPunch(10L, 1L, "proc-1"));
        when(securityFrameworkService.hasPermission(QUERY_PERMISSION)).thenReturn(false);
        stubHistoricAssignee("proc-1", 9L, 0L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.getPunchCorrection(10L, 9L));
        assertEquals(OA_PUNCH_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void concurrentSecondAndThird_onlyQuotaSucceeds() throws Exception {
        ReentrantLock monthLock = new ReentrantLock();
        List<BpmOAPunchCorrectionDO> store = new ArrayList<>();
        store.add(quotaRow(AUG_15, BpmTaskStatusEnum.RUNNING.getStatus()));
        AtomicInteger idSeq = new AtomicInteger(1);
        when(punchCorrectionMapper.selectByUserAndMonthForUpdate(eq(1L), any(), any())).thenAnswer(invocation -> {
            monthLock.lock();
            return copyStore(store);
        });
        doAnswer(invocation -> {
            BpmOAPunchCorrectionDO row = invocation.getArgument(0);
            row.setId((long) idSeq.incrementAndGet());
            store.add(copyRow(row));
            return 1;
        }).when(punchCorrectionMapper).insert(any(BpmOAPunchCorrectionDO.class));
        when(processInstanceApi.createProcessInstance(eq(1L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenAnswer(invocation -> CommonResult.success("pi-" + idSeq.get()));

        CyclicBarrier start = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                int day = 16 + i;
                pool.submit(() -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        service.createPunchCorrection(1L,
                                validCreateReq(LocalDate.of(2026, 8, day), PUNCH_TIME.plusDays(day - 15)));
                        successCount.incrementAndGet();
                    } catch (ServiceException ex) {
                        assertEquals(OA_PUNCH_MONTH_QUOTA_EXCEEDED.getCode(), ex.getCode());
                        failCount.incrementAndGet();
                    } catch (Exception ex) {
                        fail(ex);
                    } finally {
                        if (monthLock.isHeldByCurrentThread()) {
                            monthLock.unlock();
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
        verify(punchCorrectionMapper, times(1)).insert(any(BpmOAPunchCorrectionDO.class));
    }

    private void stubInsert(Long id) {
        doAnswer(invocation -> {
            BpmOAPunchCorrectionDO punch = invocation.getArgument(0);
            punch.setId(id);
            return 1;
        }).when(punchCorrectionMapper).insert(any(BpmOAPunchCorrectionDO.class));
    }

    private static BpmOAPunchCorrectionCreateReqVO validCreateReq(LocalDate punchDate, LocalDateTime punchTime) {
        BpmOAPunchCorrectionCreateReqVO req = new BpmOAPunchCorrectionCreateReqVO();
        req.setPunchDate(punchDate);
        req.setPunchTime(punchTime);
        req.setReason("忘记打卡");
        req.setAttachmentUrls(List.of("https://file.example/pc.png"));
        return req;
    }

    private static BpmOAPunchCorrectionDO quotaRow(LocalDate punchDate, Integer status) {
        return BpmOAPunchCorrectionDO.builder()
                .id(100L)
                .userId(1L)
                .punchDate(punchDate)
                .punchTime(punchDate.atTime(9, 0))
                .status(status)
                .build();
    }

    private static BpmOAPunchCorrectionDO ownedPunch(Long id, Long userId, String processInstanceId) {
        return BpmOAPunchCorrectionDO.builder()
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

    private void stubHistoricAssignee(String processInstanceId, Long userId, long count) {
        HistoryService historyService = mock(HistoryService.class);
        HistoricTaskInstanceQuery historyQuery = mock(HistoricTaskInstanceQuery.class);
        when(historyServiceProvider.getIfAvailable()).thenReturn(historyService);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.processInstanceId(processInstanceId)).thenReturn(historyQuery);
        when(historyQuery.taskAssignee(String.valueOf(userId))).thenReturn(historyQuery);
        when(historyQuery.taskOwner(String.valueOf(userId))).thenReturn(historyQuery);
        when(historyQuery.count()).thenReturn(count);
    }

    private static List<BpmOAPunchCorrectionDO> copyStore(List<BpmOAPunchCorrectionDO> store) {
        List<BpmOAPunchCorrectionDO> copy = new ArrayList<>();
        for (BpmOAPunchCorrectionDO row : store) {
            copy.add(copyRow(row));
        }
        return copy;
    }

    private static BpmOAPunchCorrectionDO copyRow(BpmOAPunchCorrectionDO row) {
        return BpmOAPunchCorrectionDO.builder()
                .id(row.getId())
                .userId(row.getUserId())
                .punchDate(row.getPunchDate())
                .punchTime(row.getPunchTime())
                .reason(row.getReason())
                .status(row.getStatus())
                .processInstanceId(row.getProcessInstanceId())
                .build();
    }

}
