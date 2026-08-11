package cn.iocoder.yudao.module.finance.service.contract;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceContractApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.finance.service.contract.FinanceContractApplicationServiceImpl.PROCESS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class FinanceContractApplicationServiceImplTest {

    private FinanceContractApplicationMapper applicationMapper;
    private FinanceContractApplicationNoRedisDAO applicationNoRedisDAO;
    private BpmProcessInstanceApi processInstanceApi;
    private FinanceCustomerCompanyService customerCompanyService;
    private ObjectProvider<TaskService> taskServiceProvider;
    private DictDataApi dictDataApi;
    private FinanceContractApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(FinanceContractApplicationMapper.class);
        applicationNoRedisDAO = mock(FinanceContractApplicationNoRedisDAO.class);
        processInstanceApi = mock(BpmProcessInstanceApi.class);
        customerCompanyService = mock(FinanceCustomerCompanyService.class);
        taskServiceProvider = mock(ObjectProvider.class);
        dictDataApi = mock(DictDataApi.class);
        when(dictDataApi.validateDictDataList(anyString(), anyCollection())).thenReturn(CommonResult.success(true));
        when(taskServiceProvider.getIfAvailable()).thenReturn(null);
        service = new FinanceContractApplicationServiceImpl(
                applicationMapper, applicationNoRedisDAO, processInstanceApi, customerCompanyService,
                taskServiceProvider, dictDataApi);

        when(applicationNoRedisDAO.generate(any(LocalDate.class))).thenReturn("CT-20260731-1");
        when(customerCompanyService.getEnabledCustomerCompany(50L)).thenReturn(
                FinanceCustomerCompanyDO.builder()
                        .id(50L)
                        .name("客商A")
                        .taxNo("91110000MA0000000X")
                        .status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                        .build());
        doAnswer(invocation -> {
            FinanceContractApplicationDO app = invocation.getArgument(0);
            app.setId(100L);
            return 1;
        }).when(applicationMapper).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void createAndStartShouldFailWhenAmountMissingWithoutAmountNa() {
        FinanceContractApplicationCreateAndStartReqVO req = validReq();
        req.setAmountNa(false);
        req.setContractAmount(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req, 200L));
        assertEquals(CONTRACT_APPLICATION_AMOUNT_INVALID.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceContractApplicationDO.class));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createAndStartShouldFailWhenPurchaseMissingPreProcess() {
        FinanceContractApplicationCreateAndStartReqVO req = validReq();
        req.setFileType("采购合同");
        req.setPreProcessRef(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req, 200L));
        assertEquals(CONTRACT_APPLICATION_PRE_PROCESS_REQUIRED.getCode(), ex.getCode());
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createAndStartShouldRejectUnknownProductType() {
        when(dictDataApi.validateDictDataList(anyString(), anyCollection()))
                .thenThrow(new IllegalArgumentException("unknown dictionary value"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(validReq(), 200L));
        assertEquals(CONTRACT_APPLICATION_PRODUCT_TYPE_INVALID.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void createAndStartShouldRejectBlankProductType() {
        // EXP-70 #9：新合同产品必填
        FinanceContractApplicationCreateAndStartReqVO req = validReq();
        req.setProductType(null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req, 200L));
        assertEquals(CONTRACT_APPLICATION_FIELD_REQUIRED.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void createAndStartShouldOccupyAndWriteProcessInstanceId() {
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-1"));

        Long id = service.createAndStart(validReq(), 200L);
        assertEquals(100L, id);

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(200L), bpmCaptor.capture());
        BpmProcessInstanceCreateReqDTO bpmReq = bpmCaptor.getValue();
        assertEquals(PROCESS_KEY, bpmReq.getProcessDefinitionKey());
        assertEquals("100", bpmReq.getBusinessKey());
        assertEquals(Boolean.FALSE, bpmReq.getVariables().get("needMail"));

        ArgumentCaptor<FinanceContractApplicationDO> updateCaptor =
                ArgumentCaptor.forClass(FinanceContractApplicationDO.class);
        verify(applicationMapper).updateById(updateCaptor.capture());
        assertEquals("proc-1", updateCaptor.getValue().getProcessInstanceId());
    }

    @Test
    void createAndStartShouldAllowAmountNaWithoutAmount() {
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-2"));

        FinanceContractApplicationCreateAndStartReqVO req = validReq();
        req.setAmountNa(true);
        req.setContractAmount(null);
        assertDoesNotThrow(() -> service.createAndStart(req, 200L));
        verify(applicationMapper).insert(argThat((FinanceContractApplicationDO app) ->
                Boolean.TRUE.equals(app.getAmountNa()) && app.getContractAmount() == null));
    }

    @Test
    void cancelShouldRejectWhenAtSealNode() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .currentNodeKey("seal")
                .voided(false)
                .build());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(100L, 200L));
        assertEquals(CONTRACT_APPLICATION_CANCEL_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    void cancelShouldRejectWhenNotOwner() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .currentNodeKey("legal")
                .voided(false)
                .build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(100L, 999L));
        assertEquals(CONTRACT_APPLICATION_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void cancelShouldCancelFlowableAndMarkCancelled() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .processInstanceId("proc-x")
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .currentNodeKey("legal")
                .voided(false)
                .build());
        when(processInstanceApi.cancelProcessInstanceByStartUser(
                eq(200L), eq("proc-x"), anyString(), anyCollection()))
                .thenReturn(CommonResult.success(true));
        when(applicationMapper.update(isNull(), any())).thenReturn(1);

        service.cancel(100L, 200L);

        verify(processInstanceApi).cancelProcessInstanceByStartUser(
                eq(200L), eq("proc-x"), anyString(),
                argThat(keys -> keys != null
                        && keys.contains("taskSeal")
                        && keys.contains("taskArchive")
                        && keys.contains("taskMail")));
        verify(applicationMapper).update(isNull(), any());
    }

    @Test
    void cancelShouldNotMarkCancelledWhenBpmRejectsForbiddenTask() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .processInstanceId("proc-x")
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .currentNodeKey("gm") // 台账仍显示用印前，但 BPM 已进入 seal
                .voided(false)
                .build());
        when(processInstanceApi.cancelProcessInstanceByStartUser(
                eq(200L), eq("proc-x"), anyString(), anyCollection()))
                .thenThrow(new ServiceException(CONTRACT_APPLICATION_CANCEL_NOT_ALLOWED));

        assertThrows(ServiceException.class, () -> service.cancel(100L, 200L));
        verify(applicationMapper, never()).update(isNull(), any());
        verify(applicationMapper, never()).updateById(any(FinanceContractApplicationDO.class));
    }

    @Test
    void resubmitShouldRejectWhenNotRejected() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .voided(false)
                .build());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.resubmit(100L, toResubmit(validReq()), 200L));
        assertEquals(CONTRACT_APPLICATION_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void resubmitShouldRejectWhenNotOwner() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .approvalStatus(FinanceContractApprovalStatusEnum.REJECTED.getStatus())
                .voided(false)
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.resubmit(100L, toResubmit(validReq()), 999L));
        assertEquals(CONTRACT_APPLICATION_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void onApprovalOutcomeShouldBeIdempotentForSameOutcome() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .processInstanceId("proc-1")
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(false)
                .build());
        assertDoesNotThrow(() -> service.onApprovalOutcome(100L, "APPROVED", "proc-1"));
        verify(applicationMapper, never()).update(isNull(), any());
        verify(applicationMapper, never()).updateById(any(FinanceContractApplicationDO.class));
    }

    @Test
    void onApprovalOutcomeShouldIgnoreStaleProcessInstance() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .processInstanceId("proc-new")
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .voided(false)
                .build());
        assertDoesNotThrow(() -> service.onApprovalOutcome(100L, "REJECTED", "proc-old"));
        verify(applicationMapper, never()).update(isNull(), any());
        verify(applicationMapper, never()).updateById(any(FinanceContractApplicationDO.class));
    }

    @Test
    void onApprovalOutcomeApprovedShouldRequireExecutionEvidence() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .processInstanceId("proc-1")
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .voided(false)
                .needMail(false)
                .sealFileUrl(null)
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.onApprovalOutcome(100L, "APPROVED", "proc-1"));
        assertEquals(CONTRACT_APPLICATION_APPROVED_EVIDENCE_INCOMPLETE.getCode(), ex.getCode());
    }

    @Test
    void onApprovalOutcomeApprovedShouldSucceedWithEvidence() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .processInstanceId("proc-1")
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .voided(false)
                .needMail(false)
                .sealFileUrl("https://x/seal.pdf")
                .archivedAt(LocalDateTime.now())
                .build());
        when(applicationMapper.update(isNull(), any())).thenReturn(1);
        assertDoesNotThrow(() -> service.onApprovalOutcome(100L, "APPROVED", "proc-1"));
        verify(applicationMapper).update(isNull(), any());
    }

    @Test
    void onApprovalOutcomeApprovedShouldRequireMailWhenNeedMail() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .processInstanceId("proc-1")
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .voided(false)
                .needMail(true)
                .sealFileUrl("https://x/seal.pdf")
                .archivedAt(LocalDateTime.now())
                .mailTrackingNo(null)
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.onApprovalOutcome(100L, "APPROVED", "proc-1"));
        assertEquals(CONTRACT_APPLICATION_APPROVED_EVIDENCE_INCOMPLETE.getCode(), ex.getCode());
    }

    @Test
    void getApplicationShouldDenyNonOwnerWithoutManageAll() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .processInstanceId("proc-1")
                .build());
        // TaskService unavailable → no task-context path
        when(taskServiceProvider.getIfAvailable()).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.getApplication(100L, 999L, false));
        assertEquals(CONTRACT_APPLICATION_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void getApplicationShouldAllowOwnerWithoutManageAll() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .build());
        FinanceContractApplicationDO app = service.getApplication(100L, 200L, false);
        assertEquals(100L, app.getId());
    }

    @Test
    void getApplicationShouldAllowManageAllForNonOwner() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .build());
        FinanceContractApplicationDO app = service.getApplication(100L, 999L, true);
        assertEquals(100L, app.getId());
    }

    @Test
    void getApplicationShouldAllowActiveTaskCandidate() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .processInstanceId("proc-1")
                .build());
        org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(taskService);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.taskCandidateOrAssigned("999")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);

        FinanceContractApplicationDO app = service.getApplication(100L, 999L, false);
        assertEquals(100L, app.getId());
        verify(taskQuery).taskCandidateOrAssigned("999");
    }

    @Test
    void canAccessDetailShouldAllowOwnerWithoutQueryPath() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .build());
        assertTrue(service.canAccessDetail(100L, 200L));
    }

    @Test
    void canAccessDetailShouldAllowTaskCandidate() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .processInstanceId("proc-1")
                .build());
        org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(taskService);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.taskCandidateOrAssigned("999")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        assertTrue(service.canAccessDetail(100L, 999L));
    }

    @Test
    void canAccessDetailShouldDenyStrangerAndMissing() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .processInstanceId("proc-1")
                .build());
        when(applicationMapper.selectById(404L)).thenReturn(null);
        org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(taskService);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.taskCandidateOrAssigned("999")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(0L);
        assertFalse(service.canAccessDetail(100L, 999L));
        assertFalse(service.canAccessDetail(404L, 200L));
        assertFalse(service.canAccessDetail(null, 200L));
    }

    @Test
    void getApplicationShouldDenyWhenNotTaskCandidate() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .processInstanceId("proc-1")
                .build());
        org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        org.flowable.task.api.TaskQuery taskQuery = mock(org.flowable.task.api.TaskQuery.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(taskService);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.taskCandidateOrAssigned("999")).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(0L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.getApplication(100L, 999L, false));
        assertEquals(CONTRACT_APPLICATION_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void resubmitShouldClaimRejectedAndStartProcess() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicationNo("CT-1")
                .applicantUserId(200L)
                .approvalStatus(FinanceContractApprovalStatusEnum.REJECTED.getStatus())
                .voided(false)
                .needMail(false)
                .build());
        when(applicationMapper.update(isNull(), any())).thenReturn(1);
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-new"));

        assertDoesNotThrow(() -> service.resubmit(100L, toResubmit(validReq()), 200L));
        verify(processInstanceApi).createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class));
        verify(applicationMapper).updateById(argThat((FinanceContractApplicationDO u) ->
                "proc-new".equals(u.getProcessInstanceId())));
    }

    @Test
    void resubmitShouldNotStartWhenClaimLoses() {
        when(applicationMapper.selectById(100L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(100L)
                .applicantUserId(200L)
                .approvalStatus(FinanceContractApprovalStatusEnum.REJECTED.getStatus())
                .voided(false)
                .build());
        // 条件更新 0 行：另一请求已 claim
        when(applicationMapper.update(isNull(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.resubmit(100L, toResubmit(validReq()), 200L));
        assertEquals(CONTRACT_APPLICATION_STATUS_INVALID.getCode(), ex.getCode());
        verifyNoInteractions(processInstanceApi);
        verify(applicationMapper, never()).updateById(any(FinanceContractApplicationDO.class));
    }

    @Test
    void getApplicationPageShouldForceApplicantForNonManage() {
        FinanceContractApplicationPageReqVO pageReq = new FinanceContractApplicationPageReqVO();
        pageReq.setApplicantUserId(1L); // client spoof
        service.getApplicationPage(pageReq, 200L, false);
        assertEquals(200L, pageReq.getApplicantUserId());
        verify(applicationMapper).selectPage(pageReq);
    }

    private static FinanceContractApplicationCreateAndStartReqVO validReq() {
        FinanceContractApplicationCreateAndStartReqVO req = new FinanceContractApplicationCreateAndStartReqVO();
        req.setCounterpartyCompanyId(50L);
        req.setAmountNa(false);
        req.setContractAmount(new BigDecimal("1000.00"));
        req.setSignCompany("A公司");
        req.setFileName("销售合同-测试");
        req.setFileType("销售合同");
        req.setProductType("软件");
        req.setRebateRatio("10%");
        req.setSettlementMethod("月结");
        req.setCopyCount(2);
        req.setSealTypes("[\"合同专用章\"]");
        req.setNeedMail(false);
        req.setDraftFileUrl("https://example.com/draft.pdf");
        return req;
    }

    private static FinanceContractApplicationResubmitReqVO toResubmit(FinanceContractApplicationCreateAndStartReqVO src) {
        FinanceContractApplicationResubmitReqVO req = new FinanceContractApplicationResubmitReqVO();
        req.setCounterpartyCompanyId(src.getCounterpartyCompanyId());
        req.setAmountNa(src.getAmountNa());
        req.setContractAmount(src.getContractAmount());
        req.setSignCompany(src.getSignCompany());
        req.setFileName(src.getFileName());
        req.setFileType(src.getFileType());
        req.setProductType(src.getProductType());
        req.setRebateRatio(src.getRebateRatio());
        req.setSettlementMethod(src.getSettlementMethod());
        req.setCopyCount(src.getCopyCount());
        req.setSealTypes(src.getSealTypes());
        req.setNeedMail(src.getNeedMail());
        req.setDraftFileUrl(src.getDraftFileUrl());
        return req;
    }
}
