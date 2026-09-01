package cn.iocoder.yudao.module.finance.service.payment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentRecordPayReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinancePaymentApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentReasonEnum;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentTimingEnum;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.BpmProcessVariableConstants;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancePaymentApplicationServiceImplTest {

    private FinancePaymentApplicationMapper mapper;
    private FinancePaymentApplicationNoRedisDAO noRedisDAO;
    private FinanceBpmProcessInstanceApi processInstanceApi;
    private FinanceCustomerCompanyService customerCompanyService;
    private FinancePaymentPredocService predocService;
    private FinanceContractApplicationMapper contractMapper;
    private AdminUserApi adminUserApi;
    private DictDataApi dictDataApi;
    private ObjectProvider<org.flowable.engine.TaskService> taskProvider;
    private ObjectProvider<org.flowable.engine.HistoryService> historyProvider;
    private ObjectProvider<cn.iocoder.yudao.module.system.api.dept.DeptApi> deptProvider;
    private FinanceEntityCompanyResolver entityCompanyResolver;
    private cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService companyBankAccountService;
    private cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper payLineMapper;
    private cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentSalaryLineMapper salaryLineMapper;
    private cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentTaxLineMapper taxLineMapper;
    private FinancePaymentApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinancePaymentApplicationMapper.class);
        noRedisDAO = mock(FinancePaymentApplicationNoRedisDAO.class);
        processInstanceApi = mock(FinanceBpmProcessInstanceApi.class);
        customerCompanyService = mock(FinanceCustomerCompanyService.class);
        predocService = mock(FinancePaymentPredocService.class);
        contractMapper = mock(FinanceContractApplicationMapper.class);
        adminUserApi = mock(AdminUserApi.class);
        dictDataApi = mock(DictDataApi.class);
        entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.flowable.engine.TaskService> tp = mock(ObjectProvider.class);
        taskProvider = tp;
        when(taskProvider.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<org.flowable.engine.HistoryService> hp = mock(ObjectProvider.class);
        historyProvider = hp;
        when(historyProvider.getIfAvailable()).thenReturn(null);
        @SuppressWarnings("unchecked")
        ObjectProvider<cn.iocoder.yudao.module.system.api.dept.DeptApi> dp = mock(ObjectProvider.class);
        deptProvider = dp;
        when(deptProvider.getIfAvailable()).thenReturn(null);

        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(1L);
        user.setDeptId(10L);
        when(adminUserApi.getUser(anyLong())).thenReturn(CommonResult.success(user));
        // PAY-R10：默认字典合法
        when(dictDataApi.validateDictDataList(anyString(), anyCollection()))
                .thenReturn(CommonResult.success(true));
        when(entityCompanyResolver.requireByDeptId(20L))
                .thenReturn(new FinanceEntityCompanyResolver.ResolvedCompany(20L, "主体甲", "USD"));

        companyBankAccountService = mock(cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService.class);
        payLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper.class);
        salaryLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentSalaryLineMapper.class);
        taxLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentTaxLineMapper.class);
        when(payLineMapper.sumPayAmountByApplicationId(anyLong())).thenReturn(java.math.BigDecimal.ZERO);
        when(payLineMapper.selectByApplicationId(anyLong())).thenReturn(java.util.List.of());
        // F5：悲观锁读默认委托到 selectById
        when(mapper.selectByIdForUpdate(anyLong())).thenAnswer(inv -> mapper.selectById(inv.getArgument(0)));
        service = new FinancePaymentApplicationServiceImpl(
                mapper, noRedisDAO, processInstanceApi, customerCompanyService,
                predocService, contractMapper, taskProvider, historyProvider, adminUserApi, dictDataApi,
                deptProvider, entityCompanyResolver, companyBankAccountService, payLineMapper,
                salaryLineMapper, taxLineMapper);
        injectBusinessStaffSupport();
        cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport participant =
                mock(cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport.class);
        when(participant.canReadBill(any(), any(), any())).thenAnswer(inv -> {
            Long userId = inv.getArgument(0);
            Long applicantId = inv.getArgument(1);
            return userId != null && userId.equals(applicantId);
        });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "processParticipantSupport", participant);
        when(noRedisDAO.generate(any(LocalDate.class))).thenReturn("PAY-20260806-1");
        doAnswer(inv -> {
            FinancePaymentApplicationDO a = inv.getArgument(0);
            a.setId(100L);
            return 1;
        }).when(mapper).insert(any(FinancePaymentApplicationDO.class));

        when(contractMapper.selectById(51L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(51L)
                .applicantUserId(1L)
                .fileType("付款业务合同")
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(false)
                .settlementMethod("月结30天")
                .build());
        when(customerCompanyService.getEnabledSupplierCompany(anyLong())).thenReturn(
                FinanceCustomerCompanyDO.builder()
                        .id(9L).name("供应商甲").bankName("行").bankAccount("6222")
                        .isSupplier(true).status(0).build());

        CommonResult<String> pi = mock(CommonResult.class);
        when(pi.getCheckedData()).thenReturn("proc-1");
        when(processInstanceApi.createProcessInstance(anyLong(), any())).thenReturn(pi);
        when(processInstanceApi.createProcessInstanceByBusiness(anyLong(), any())).thenReturn(pi);
    }

    private void injectBusinessStaffSupport() {
        cn.iocoder.yudao.module.finance.service.common.FinanceBusinessStaffSupport staffSupport =
                mock(cn.iocoder.yudao.module.finance.service.common.FinanceBusinessStaffSupport.class);
        when(staffSupport.resolve(anyLong(), any())).thenAnswer(inv -> inv.getArgument(0));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "businessStaffSupport", staffSupport);
        cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport participant =
                mock(cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport.class);
        when(participant.canReadBill(any(), any(), any())).thenAnswer(inv -> {
            Long userId = inv.getArgument(0);
            Long applicantId = inv.getArgument(1);
            return userId != null && userId.equals(applicantId);
        });
        org.springframework.test.util.ReflectionTestUtils.setField(service, "processParticipantSupport", participant);
    }

    private FinancePaymentApplicationCreateAndStartReqVO baseReq() {
        FinancePaymentApplicationCreateAndStartReqVO req = new FinancePaymentApplicationCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPaymentReason(FinancePaymentReasonEnum.BUSINESS.getCode());
        req.setPayeeCompanyId(9L);
        req.setEntityCompanyDeptId(20L);
        req.setApplyAmount(new BigDecimal("100.00"));
        req.setCurrency("CNY");
        req.setBusinessSettlementTerm("2026-08-31");
        req.setCostProject("office_purchase");
        req.setRelatedContractApplicationId(51L);
        req.setEvidenceFileUrls(List.of("https://x/a.pdf"));
        return req;
    }

    @Test
    void createAndStartBusinessOk() {
        Long id = service.createAndStart(baseReq(), 1L);
        assertEquals(100L, id);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(FinancePaymentApplicationStatusEnum.PENDING.getStatus(), cap.getValue().getStatus());
        assertEquals("供应商甲", cap.getValue().getPayeeName());
        verify(processInstanceApi).createProcessInstanceByBusiness(eq(1L), any());
    }

    @Test
    void createPurchaseRequiresRef() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setPaymentReason(FinancePaymentReasonEnum.PURCHASE.getCode());
        req.setRelatedContractApplicationId(null);
        when(predocService.validateAndSummarizePurchaseRef(isNull(), eq(1L)))
                .thenThrow(new ServiceException(PAYMENT_PURCHASE_REF_INVALID));
        // blank purchase id path
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_PURCHASE_REF_INVALID.getCode(), ex.getCode());
    }

    @Test
    void createPurchaseWithRefOk() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setPaymentReason(FinancePaymentReasonEnum.PURCHASE.getCode());
        req.setRelatedContractApplicationId(null);
        req.setPurchaseProcessInstanceId("pi-ok");
        when(predocService.validateAndSummarizePurchaseRef("pi-ok", 1L)).thenReturn("采购摘要");
        Long id = service.createAndStart(req, 1L);
        assertEquals(100L, id);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals("pi-ok", cap.getValue().getPurchaseProcessInstanceId());
        assertEquals("采购摘要", cap.getValue().getPurchaseSnapshot());
        assertNull(cap.getValue().getCostProject());
    }

    @Test
    void createBusinessWithoutCostProjectFails() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setCostProject("  ");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_APPLICATION_FIELD_REQUIRED.getCode(), ex.getCode());
        verify(mapper, never()).insert(any(FinancePaymentApplicationDO.class));
    }

    @Test
    void createOtherWithoutCostProjectOk() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setPaymentReason(FinancePaymentReasonEnum.OTHER.getCode());
        req.setRelatedContractApplicationId(null);
        req.setCostProject(null);
        Long id = service.createAndStart(req, 1L);
        assertEquals(100L, id);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertNull(cap.getValue().getCostProject());
        verify(dictDataApi, never()).validateDictDataList(eq("finance_product_type"), anyCollection());
    }

    @Test
    void createPurchaseIgnoresSubmittedCostProject() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setPaymentReason(FinancePaymentReasonEnum.PURCHASE.getCode());
        req.setRelatedContractApplicationId(null);
        req.setPurchaseProcessInstanceId("pi-ok");
        req.setCostProject("软件");
        when(predocService.validateAndSummarizePurchaseRef("pi-ok", 1L)).thenReturn("采购摘要");
        Long id = service.createAndStart(req, 1L);
        assertEquals(100L, id);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertNull(cap.getValue().getCostProject());
        verify(dictDataApi, never()).validateDictDataList(eq("finance_product_type"), anyCollection());
    }

    @Test
    void amountMustBePositive() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setApplyAmount(BigDecimal.ZERO);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_APPLICATION_AMOUNT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void evidenceRequired() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setEvidenceFileUrls(List.of("  "));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_APPLICATION_EVIDENCE_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void sumPaidDelegates() {
        when(mapper.sumPaidByPayee(9L)).thenReturn(new BigDecimal("50.00"));
        assertEquals(new BigDecimal("50.00"), service.sumPaidByPayee(9L));
    }

    @Test
    void onApprovalOutcomeApprovedMapsToWaitPay() {
        when(mapper.selectByIdForUpdate(2L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(2L).status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("p2").build());
        when(mapper.update(isNull(), any())).thenReturn(1);
        service.onApprovalOutcome(2L, "APPROVED", "p2");
        verify(mapper).update(isNull(), any());
    }

    @Test
    void onApprovalOutcomeWaitPayIdempotent() {
        when(mapper.selectByIdForUpdate(1L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(1L).status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("p1").build());
        service.onApprovalOutcome(1L, "WAIT_PAY", "p1");
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void onApprovalOutcomeApprovedMarksWaitPayWithoutEvidence() {
        when(mapper.selectById(70L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(70L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-70")
                .build());
        when(mapper.update(isNull(), any())).thenReturn(1);
        service.onApprovalOutcome(70L, "APPROVED", "pi-70");
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        assertTrue(updateWritesStatus(cap.getValue(), "WAIT_PAY"));
        assertFalse(updateWritesStatus(cap.getValue(), "PAID"));
    }

    @Test
    void assertCashierEvidenceForCompleteAllowsEmpty() {
        when(mapper.selectById(3L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(3L).status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus()).build());
        assertDoesNotThrow(() -> service.assertCashierEvidenceForComplete(3L));
    }

    @Test
    void onApprovalOutcomeApprovedMapsToWaitPayWithoutEvidence() {
        when(mapper.selectByIdForUpdate(21L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(21L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-21")
                .build());
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.onApprovalOutcome(21L, "APPROVED", "pi-21");

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        assertTrue(cap.getValue().getParamNameValuePairs().containsValue("WAIT_PAY"));
    }

    @Test
    void recordPayRequiresCashierFields() {
        when(mapper.selectById(1L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(1L).status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("p1").build());
        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(1L);
        req.setTaskId("t1");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_APPLICATION_CASHIER_FIELDS_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void recordPayRejectsWhenNotTaskCandidate() {
        when(mapper.selectById(4L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(4L).status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-4")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .build());

        org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        org.flowable.task.api.TaskQuery tq = mock(org.flowable.task.api.TaskQuery.class);
        org.flowable.task.api.Task task = mock(org.flowable.task.api.Task.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("task-x")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);
        when(task.getTaskDefinitionKey()).thenReturn("taskCashier");
        when(task.getProcessInstanceId()).thenReturn("pi-4");
        when(tq.taskCandidateOrAssigned("99")).thenReturn(tq);
        when(tq.count()).thenReturn(0L);

        @SuppressWarnings("unchecked")
        ObjectProvider<org.flowable.engine.TaskService> taskProvider = mock(ObjectProvider.class);
        when(taskProvider.getIfAvailable()).thenReturn(taskService);
        companyBankAccountService = mock(cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService.class);
        payLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper.class);
        salaryLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentSalaryLineMapper.class);
        taxLineMapper = mock(cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentTaxLineMapper.class);
        when(payLineMapper.sumPayAmountByApplicationId(anyLong())).thenReturn(java.math.BigDecimal.ZERO);
        when(payLineMapper.selectByApplicationId(anyLong())).thenReturn(java.util.List.of());
        when(mapper.selectByIdForUpdate(anyLong())).thenAnswer(inv -> mapper.selectById(inv.getArgument(0)));
        service = new FinancePaymentApplicationServiceImpl(
                mapper, noRedisDAO, processInstanceApi, customerCompanyService,
                predocService, contractMapper, taskProvider, historyProvider, adminUserApi, dictDataApi,
                deptProvider, entityCompanyResolver, companyBankAccountService, payLineMapper,
                salaryLineMapper, taxLineMapper);
        injectBusinessStaffSupport();
        cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport participant =
                mock(cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport.class);
        when(participant.canReadBill(any(), any(), any())).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "processParticipantSupport", participant);

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(4L);
        req.setTaskId("task-x");
        req.setCompanyBankAccountId(77L);
        req.setActualPayDate(LocalDate.now());
        req.setPayVoucherUrl("http://voucher");
        req.setIdempotencyKey("idem-test");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 99L));
        assertEquals(PAYMENT_APPLICATION_TASK_INVALID.getCode(), ex.getCode());
    }

    @Test
    void ordinaryCreateRejectsSalaryReason() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setPaymentReason(FinancePaymentReasonEnum.SALARY.getCode());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_APPLICATION_REASON_SALARY_TAX_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void recordPayRequiresAccount() {
        when(mapper.selectById(1L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(1L).status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("p1")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .build());
        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(1L);
        req.setTaskId("t1");
        req.setActualPayDate(LocalDate.now());
        req.setPayVoucherUrl("http://voucher");
        req.setIdempotencyKey("idem-test");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_APPLICATION_PAY_ACCOUNT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void maskAccountNoKeepsLast4() {
        assertEquals("****1234",
                cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService
                        .maskAccountNo("6222021234"));
    }

    @Test
    void recordPayUsesForUpdateLock() {
        when(mapper.selectByIdForUpdate(30L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(30L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-30")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .applicationKind("ORDINARY")
                .build());
        org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        org.flowable.task.api.TaskQuery tq = mock(org.flowable.task.api.TaskQuery.class);
        org.flowable.task.api.Task task = mock(org.flowable.task.api.Task.class);
        when(taskProvider.getIfAvailable()).thenReturn(taskService);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("t-30")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);
        when(task.getTaskDefinitionKey()).thenReturn("taskCashier");
        when(task.getProcessInstanceId()).thenReturn("pi-30");
        when(task.getId()).thenReturn("t-30");
        when(tq.taskCandidateOrAssigned("1")).thenReturn(tq);
        when(tq.count()).thenReturn(1L);
        var account = cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                .id(77L).entityCompanyDeptId(20L).accountName("基本户").bankName("工行")
                .accountHolder("甲").accountNo("6222").currency("CNY").status(0).build();
        when(companyBankAccountService.get(77L)).thenReturn(account);
        when(companyBankAccountService.requireEnabledForEntityCompany(eq(77L), eq(20L))).thenReturn(account);
        when(payLineMapper.sumPayAmountByApplicationId(30L)).thenReturn(BigDecimal.ZERO);
        when(mapper.update(isNull(), any())).thenReturn(1);

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(30L);
        req.setTaskId("t-30");
        req.setCompanyBankAccountId(77L);
        req.setActualPayDate(LocalDate.now());
        req.setPayVoucherUrl("http://voucher");
        req.setIdempotencyKey("idem-test");
        service.recordPay(req, 1L);

        verify(mapper, atLeastOnce()).selectByIdForUpdate(30L);
        verify(payLineMapper).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
        verify(taskService).complete("t-30");
    }

    @Test
    void recordPayWaitPayWithoutTaskMarksPaid() {
        when(mapper.selectByIdForUpdate(71L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(71L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-71")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .applicationKind("ORDINARY")
                .build());
        var account = cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                .id(77L).entityCompanyDeptId(20L).accountName("基本户").bankName("工行")
                .accountHolder("甲").accountNo("6222").currency("CNY").status(0).build();
        when(companyBankAccountService.get(77L)).thenReturn(account);
        when(companyBankAccountService.requireEnabledForEntityCompany(eq(77L), eq(20L))).thenReturn(account);
        when(payLineMapper.sumPayAmountByApplicationId(71L)).thenReturn(BigDecimal.ZERO);
        when(mapper.update(isNull(), any())).thenReturn(1);

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(71L);
        req.setCompanyBankAccountId(77L);
        req.setActualPayDate(LocalDate.now());
        req.setPayVoucherUrl("http://voucher");
        req.setIdempotencyKey("idem-list");
        service.recordPay(req, 1L);

        verify(payLineMapper).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        assertTrue(updateWritesStatus(cap.getValue(), "PAID"));
        verify(taskProvider, never()).getIfAvailable();
    }

    @Test
    void recordPayPendingWithoutTaskRejected() {
        when(mapper.selectByIdForUpdate(72L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(72L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-72")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .build());
        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(72L);
        req.setCompanyBankAccountId(77L);
        req.setActualPayDate(LocalDate.now());
        req.setPayVoucherUrl("http://voucher");
        req.setIdempotencyKey("idem-pending");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_APPLICATION_TASK_INVALID.getCode(), ex.getCode());
        verify(payLineMapper, never()).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
    }

    @Test
    void resubmitSalaryRequiresRejected() {
        when(mapper.selectById(40L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(40L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .applicationKind("SALARY")
                .voided(false)
                .build());
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-07");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setNetSalaryAmount(new BigDecimal("100.00"));
        line.setPersonalTaxAmount(BigDecimal.ZERO);
        line.setSocialInsuranceAmount(BigDecimal.ZERO);
        req.setLines(List.of(line));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.resubmitSalary(40L, req, 1L));
        assertEquals(PAYMENT_APPLICATION_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void resubmitSalaryOkRewritesAndStartsProcess() {
        when(mapper.selectById(41L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(41L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.REJECTED.getStatus())
                .applicationKind("SALARY")
                .voided(false)
                .build());
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(payLineMapper.selectByApplicationId(41L)).thenReturn(List.of());
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-07");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setCompanyBankAccountId(77L);
        line.setNetSalaryAmount(new BigDecimal("100.00"));
        line.setPersonalTaxAmount(new BigDecimal("10.00"));
        line.setSocialInsuranceAmount(new BigDecimal("20.00"));
        req.setLines(List.of(line));
        when(companyBankAccountService.requireEnabledForEntityCompany(77L, 20L))
                .thenReturn(cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                        .id(77L).entityCompanyDeptId(20L).accountName("基本户").bankName("工行")
                        .accountHolder("甲").accountNo("622200001111").currency("CNY").status(0).build());

        service.resubmitSalary(41L, req, 1L);

        verify(salaryLineMapper).deleteByApplicationId(41L);
        verify(salaryLineMapper).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO.class));
        // 支付流水不可变：重提不得删除 pay_line
        verify(payLineMapper, never()).deleteById(any());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCap =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstanceByBusiness(eq(1L), bpmCap.capture());
        assertEquals(FinancePaymentApplicationService.PROCESS_KEY_SALARY,
                bpmCap.getValue().getProcessDefinitionKey());
    }

    @Test
    void resubmitSalaryBlockedWhenHasPayLines() {
        when(mapper.selectById(42L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(42L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.REJECTED.getStatus())
                .applicationKind("SALARY")
                .voided(false)
                .build());
        when(payLineMapper.selectByApplicationId(42L)).thenReturn(List.of(
                cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.builder()
                        .id(1L).paymentApplicationId(42L).payAmount(new BigDecimal("10.00")).build()));
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-07");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setNetSalaryAmount(new BigDecimal("100.00"));
        line.setPersonalTaxAmount(BigDecimal.ZERO);
        line.setSocialInsuranceAmount(BigDecimal.ZERO);
        req.setLines(List.of(line));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.resubmitSalary(42L, req, 1L));
        assertEquals(PAYMENT_APPLICATION_HAS_PAY_LINES.getCode(), ex.getCode());
    }

    @Test
    void rejectBlockedWhenHasPayLines() {
        when(mapper.selectByIdForUpdate(43L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(43L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-43")
                .actualPayDate(LocalDate.now())
                .payVoucherUrl("http://v")
                .build());
        when(payLineMapper.selectByApplicationId(43L)).thenReturn(List.of(
                cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.builder()
                        .id(2L).paymentApplicationId(43L).payAmount(new BigDecimal("50.00")).build()));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.onApprovalOutcome(43L, "REJECTED", "pi-43"));
        assertEquals(PAYMENT_APPLICATION_HAS_PAY_LINES.getCode(), ex.getCode());
        verify(mapper, atLeastOnce()).selectByIdForUpdate(43L);
    }

    @Test
    void recordPayRequiresIdempotencyKey() {
        when(mapper.selectByIdForUpdate(47L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(47L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-47")
                .applyAmount(new BigDecimal("10.00"))
                .currency("CNY")
                .build());
        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(47L);
        req.setTaskId("t");
        req.setCompanyBankAccountId(1L);
        req.setActualPayDate(LocalDate.now());
        req.setPayVoucherUrl("http://v");
        // 无 idempotencyKey
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_APPLICATION_IDEMPOTENCY_KEY_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void recordPayRejectsCurrencyMismatch() {
        when(mapper.selectByIdForUpdate(44L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(44L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-44")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .applicationKind("ORDINARY")
                .build());
        org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        org.flowable.task.api.TaskQuery tq = mock(org.flowable.task.api.TaskQuery.class);
        org.flowable.task.api.Task task = mock(org.flowable.task.api.Task.class);
        when(taskProvider.getIfAvailable()).thenReturn(taskService);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("t-44")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);
        when(task.getTaskDefinitionKey()).thenReturn("taskCashier");
        when(task.getProcessInstanceId()).thenReturn("pi-44");
        when(task.getId()).thenReturn("t-44");
        when(tq.taskCandidateOrAssigned("1")).thenReturn(tq);
        when(tq.count()).thenReturn(1L);
        var account = cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                .id(88L).entityCompanyDeptId(20L).accountName("美元户").bankName("行")
                .accountHolder("甲").accountNo("9999").currency("USD").status(0).build();
        when(companyBankAccountService.get(88L)).thenReturn(account);
        when(companyBankAccountService.requireEnabledForEntityCompany(eq(88L), eq(20L))).thenReturn(account);

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(44L);
        req.setTaskId("t-44");
        req.setCompanyBankAccountId(88L);
        req.setActualPayDate(LocalDate.now());
        req.setPayVoucherUrl("http://voucher");
        req.setIdempotencyKey("idem-test");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_ACCOUNT_CURRENCY_MISMATCH.getCode(), ex.getCode());
    }

    @Test
    void recordPayIdempotentWhenKeyExistsWithoutActiveTask() {
        LocalDate payDate = LocalDate.now();
        when(mapper.selectByIdForUpdate(45L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(45L)
                .status(FinancePaymentApplicationStatusEnum.PAID.getStatus())
                .processInstanceId("pi-45")
                .applyAmount(new BigDecimal("100.00"))
                .actualPayDate(payDate)
                .payVoucherUrl("http://v")
                .currency("CNY")
                .build());
        when(payLineMapper.selectByAppAndIdempotencyKey(45L, "idem-1")).thenReturn(
                cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.builder()
                        .id(9L).paymentApplicationId(45L).idempotencyKey("idem-1")
                        .companyBankAccountId(1L)
                        .payAmount(new BigDecimal("100.00"))
                        .actualPayDate(payDate)
                        .payVoucherUrl("http://v")
                        .build());
        when(payLineMapper.sumPayAmountByApplicationId(45L)).thenReturn(new BigDecimal("100.00"));

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(45L);
        req.setTaskId("gone-task");
        req.setCompanyBankAccountId(1L);
        req.setIdempotencyKey("idem-1");
        req.setActualPayDate(payDate);
        req.setPayVoucherUrl("http://v");
        // 不得因 task 无效而失败
        assertDoesNotThrow(() -> service.recordPay(req, 1L));
        verify(payLineMapper, never()).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
    }

    @Test
    void recordPayIdempotentSameKeySamePayloadReplayOk() {
        LocalDate payDate = LocalDate.of(2026, 8, 1);
        when(mapper.selectByIdForUpdate(48L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(48L)
                .status(FinancePaymentApplicationStatusEnum.PAID.getStatus())
                .processInstanceId("pi-48")
                .applyAmount(new BigDecimal("50.00"))
                .actualPayDate(payDate)
                .payVoucherUrl("http://voucher-48")
                .currency("CNY")
                .build());
        when(payLineMapper.selectByAppAndIdempotencyKey(48L, "idem-same")).thenReturn(
                cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.builder()
                        .id(10L).paymentApplicationId(48L).idempotencyKey("idem-same")
                        .companyBankAccountId(7L)
                        .payAmount(new BigDecimal("50.00"))
                        .actualPayDate(payDate)
                        .payVoucherUrl("http://voucher-48")
                        .erpVoucherNo("ERP-1")
                        .build());
        when(payLineMapper.sumPayAmountByApplicationId(48L)).thenReturn(new BigDecimal("50.00"));

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(48L);
        req.setTaskId("t-gone");
        req.setCompanyBankAccountId(7L);
        req.setPayAmount(new BigDecimal("50.00"));
        req.setIdempotencyKey("idem-same");
        req.setActualPayDate(payDate);
        req.setPayVoucherUrl("http://voucher-48");
        req.setErpVoucherNo("ERP-1");
        assertDoesNotThrow(() -> service.recordPay(req, 1L));
        verify(payLineMapper, never()).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
    }

    @Test
    void recordPayIdempotentSameKeyDifferentPayloadConflicts() {
        LocalDate payDate = LocalDate.of(2026, 8, 1);
        when(mapper.selectByIdForUpdate(49L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(49L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-49")
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .build());
        when(payLineMapper.selectByAppAndIdempotencyKey(49L, "idem-conflict")).thenReturn(
                cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.builder()
                        .id(11L).paymentApplicationId(49L).idempotencyKey("idem-conflict")
                        .companyBankAccountId(7L)
                        .payAmount(new BigDecimal("50.00"))
                        .actualPayDate(payDate)
                        .payVoucherUrl("http://voucher-a")
                        .build());

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(49L);
        req.setTaskId("t-49");
        req.setCompanyBankAccountId(7L);
        req.setPayAmount(new BigDecimal("80.00")); // 金额不同
        req.setIdempotencyKey("idem-conflict");
        req.setActualPayDate(payDate);
        req.setPayVoucherUrl("http://voucher-a");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_APPLICATION_IDEMPOTENCY_CONFLICT.getCode(), ex.getCode());
        verify(payLineMapper, never()).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
    }

    @Test
    void createAndStartSalaryUsesBusinessChannelApi() {
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-08");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setCompanyBankAccountId(77L);
        line.setNetSalaryAmount(new BigDecimal("100.00"));
        line.setPersonalTaxAmount(BigDecimal.ZERO);
        line.setSocialInsuranceAmount(BigDecimal.ZERO);
        req.setLines(List.of(line));
        when(companyBankAccountService.requireEnabledForEntityCompany(77L, 20L))
                .thenReturn(cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                        .id(77L).entityCompanyDeptId(20L).accountName("基本户").bankName("工行")
                        .accountHolder("甲").accountNo("622200001111").currency("CNY").status(0).build());

        Long id = service.createAndStartSalary(req, 1L);
        assertEquals(100L, id);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertNull(cap.getValue().getCostProject());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCap =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstanceByBusiness(eq(1L), bpmCap.capture());
        assertEquals(FinancePaymentApplicationService.PROCESS_KEY_SALARY,
                bpmCap.getValue().getProcessDefinitionKey());
        ArgumentCaptor<cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO> lineCap =
                ArgumentCaptor.forClass(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO.class);
        verify(salaryLineMapper).insert(lineCap.capture());
        assertEquals(77L, lineCap.getValue().getCompanyBankAccountId());
        assertEquals("基本户", lineCap.getValue().getAccountNameSnapshot());
    }

    @Test
    void createAndStartSalaryRejectsMissingLineAccount() {
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-08");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setNetSalaryAmount(new BigDecimal("100.00"));
        line.setPersonalTaxAmount(BigDecimal.ZERO);
        line.setSocialInsuranceAmount(BigDecimal.ZERO);
        req.setLines(List.of(line));
        when(companyBankAccountService.requireEnabledForEntityCompany(isNull(), eq(20L)))
                .thenThrow(new ServiceException(COMPANY_BANK_ACCOUNT_REQUIRED));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStartSalary(req, 1L));
        assertEquals(COMPANY_BANK_ACCOUNT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void createAndStartSalaryIncludesHousingFundInApplyAmount() {
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-08");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setNetSalaryAmount(new BigDecimal("10000.00"));
        line.setPersonalTaxAmount(BigDecimal.ZERO);
        line.setSocialInsuranceAmount(BigDecimal.ZERO);
        line.setHousingFundAmount(new BigDecimal("800.00"));
        req.setLines(List.of(line));
        when(companyBankAccountService.requireEnabledForEntityCompany(isNull(), eq(20L)))
                .thenReturn(cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                        .id(77L).entityCompanyDeptId(20L).accountName("基本户").bankName("工行")
                        .accountHolder("甲").accountNo("622200001111").currency("CNY").status(0).build());

        service.createAndStartSalary(req, 1L);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(new BigDecimal("10800.00"), cap.getValue().getApplyAmount());
        ArgumentCaptor<cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO> lineCap =
                ArgumentCaptor.forClass(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO.class);
        verify(salaryLineMapper).insert(lineCap.capture());
        assertEquals(new BigDecimal("800.00"), lineCap.getValue().getHousingFundAmount());
        assertEquals(new BigDecimal("10800.00"), lineCap.getValue().getLineTotal());
    }

    @Test
    void createAndStartSalaryTreatsNullHousingFundAsZero() {
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-08");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setNetSalaryAmount(new BigDecimal("10000.00"));
        line.setPersonalTaxAmount(BigDecimal.ZERO);
        line.setSocialInsuranceAmount(BigDecimal.ZERO);
        req.setLines(List.of(line));
        when(companyBankAccountService.requireEnabledForEntityCompany(isNull(), eq(20L)))
                .thenReturn(cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                        .id(77L).entityCompanyDeptId(20L).accountName("基本户").bankName("工行")
                        .accountHolder("甲").accountNo("622200001111").currency("CNY").status(0).build());

        service.createAndStartSalary(req, 1L);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(new BigDecimal("10000.00"), cap.getValue().getApplyAmount());
        ArgumentCaptor<cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO> lineCap =
                ArgumentCaptor.forClass(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO.class);
        verify(salaryLineMapper).insert(lineCap.capture());
        assertEquals(new BigDecimal("0.00"), lineCap.getValue().getHousingFundAmount());
        assertEquals(new BigDecimal("10000.00"), lineCap.getValue().getLineTotal());
    }

    @Test
    void createAndStartSalaryRejectsNegativeHousingFund() {
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-08");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setNetSalaryAmount(new BigDecimal("10000.00"));
        line.setPersonalTaxAmount(BigDecimal.ZERO);
        line.setSocialInsuranceAmount(BigDecimal.ZERO);
        line.setHousingFundAmount(new BigDecimal("-1.00"));
        req.setLines(List.of(line));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStartSalary(req, 1L));
        assertEquals(PAYMENT_APPLICATION_LINES_INVALID.getCode(), ex.getCode());
    }

    @Test
    void createAndStartTaxUsesBusinessChannelApi() {
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceTaxPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceTaxPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-08");
        req.setCurrency("CNY");
        req.setEvidenceFileUrls(List.of("https://x/tax.pdf"));
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentTaxLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentTaxLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setCompanyBankAccountId(77L);
        line.setVatAmount(new BigDecimal("88.00"));
        req.setLines(List.of(line));
        when(companyBankAccountService.requireEnabledForEntityCompany(77L, 20L))
                .thenReturn(cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                        .id(77L).entityCompanyDeptId(20L).accountName("基本户").bankName("工行")
                        .accountHolder("甲").accountNo("622200001111").currency("CNY").status(0).build());

        Long id = service.createAndStartTax(req, 1L);
        assertEquals(100L, id);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertNull(cap.getValue().getCostProject());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCap =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstanceByBusiness(eq(1L), bpmCap.capture());
        assertEquals(FinancePaymentApplicationService.PROCESS_KEY_TAX,
                bpmCap.getValue().getProcessDefinitionKey());
    }

    @Test
    void resubmitSalaryUsesBusinessChannelApi() {
        when(mapper.selectById(50L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(50L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.REJECTED.getStatus())
                .applicationKind("SALARY")
                .voided(false)
                .build());
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(payLineMapper.selectByApplicationId(50L)).thenReturn(List.of());
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPeriodLabel("2026-08");
        req.setCurrency("CNY");
        cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO line =
                new cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineReqVO();
        line.setEntityCompanyDeptId(20L);
        line.setCompanyBankAccountId(77L);
        line.setNetSalaryAmount(new BigDecimal("100.00"));
        line.setPersonalTaxAmount(BigDecimal.ZERO);
        line.setSocialInsuranceAmount(BigDecimal.ZERO);
        req.setLines(List.of(line));
        when(companyBankAccountService.requireEnabledForEntityCompany(77L, 20L))
                .thenReturn(cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                        .id(77L).entityCompanyDeptId(20L).accountName("基本户").bankName("工行")
                        .accountHolder("甲").accountNo("622200001111").currency("CNY").status(0).build());

        service.resubmitSalary(50L, req, 1L);
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCap =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstanceByBusiness(eq(1L), bpmCap.capture());
    }

    @Test
    void ordinaryGetRejectsSalaryKind() {
        when(mapper.selectById(46L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(46L)
                .applicantUserId(1L)
                .applicationKind("SALARY")
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.getOrdinaryApplicationForRead(46L, 1L, true));
        assertEquals(PAYMENT_APPLICATION_KIND_INVALID.getCode(), ex.getCode());
    }

    @Test
    void createPersistsEntityCompanyAndCurrencyAndBpmVars() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setCurrency("usd");
        Long id = service.createAndStart(req, 1L);
        assertEquals(100L, id);

        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(20L, cap.getValue().getEntityCompanyDeptId());
        assertEquals("主体甲", cap.getValue().getEntityCompanyName());
        assertEquals("USD", cap.getValue().getCurrency());

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCap =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstanceByBusiness(eq(1L), bpmCap.capture());
        Map<String, Object> vars = bpmCap.getValue().getVariables();
        assertEquals(20L, vars.get(BpmProcessVariableConstants.COMPANY_ID));
        assertEquals("主体甲", vars.get(BpmProcessVariableConstants.COMPANY_NAME));
        assertEquals("USD", vars.get("currency"));
        assertEquals(vars.get("applicationNo"), vars.get(BpmProcessVariableConstants.BILL_CODE));
        assertNotNull(vars.get(BpmProcessVariableConstants.BILL_CODE));
    }

    @Test
    void createRejectsInvalidCurrency() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setCurrency("EUR");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_APPLICATION_CURRENCY_INVALID.getCode(), ex.getCode());
    }

    @Test
    void createRejectsInvalidEntityCompany() {
        when(entityCompanyResolver.requireByDeptId(99L))
                .thenThrow(new ServiceException(ENTITY_COMPANY_INVALID));
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setEntityCompanyDeptId(99L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(ENTITY_COMPANY_INVALID.getCode(), ex.getCode());
    }

    @Test
    void assertFinanceSubjectRejectsBlank() {
        when(mapper.selectById(5L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(5L).status(FinancePaymentApplicationStatusEnum.PENDING.getStatus()).build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.assertFinanceSubjectForComplete(5L));
        assertEquals(PAYMENT_APPLICATION_ACCOUNTING_SUBJECT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void createUsesAuthoritativeDeptFromUser() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setApplicantDeptId(999L); // client spoof ignored
        service.createAndStart(req, 1L);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(10L, cap.getValue().getApplicantDeptId());
        assertNotNull(cap.getValue().getAmountInWords());
        assertTrue(cap.getValue().getProcessTitle().contains("付款申请"));
    }

    @Test
    void createRejectsWhenUserHasNoDept() {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(1L);
        user.setDeptId(null);
        when(adminUserApi.getUser(1L)).thenReturn(CommonResult.success(user));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(baseReq(), 1L));
        assertEquals(PAYMENT_APPLICATION_DEPT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void createRejectsAmountWithExcessScale() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setApplyAmount(new BigDecimal("0.001"));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_APPLICATION_AMOUNT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void relatedContractOnlyForBusiness() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setPaymentReason(FinancePaymentReasonEnum.PURCHASE.getCode());
        req.setPurchaseProcessInstanceId("pi-ok");
        req.setRelatedContractApplicationId(51L);
        when(predocService.validateAndSummarizePurchaseRef("pi-ok", 1L)).thenReturn("采购");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_RELATED_CONTRACT_REASON_INVALID.getCode(), ex.getCode());
    }

    @Test
    void canAccessDetailAllowsOwner() {
        when(mapper.selectById(7L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(7L).applicantUserId(1L).processInstanceId("pi-7").build());
        assertTrue(service.canAccessDetail(7L, 1L));
    }

    @Test
    void getApplicationForReadDeniesStrangerWithoutTask() {
        when(mapper.selectById(8L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(8L).applicantUserId(1L).processInstanceId("pi-8").build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.getApplicationForRead(8L, 99L, false));
        assertEquals(PAYMENT_APPLICATION_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void updateAccountingSubjectRejectsBlank() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.updateAccountingSubject(5L, "  ", "t1", 1L));
        assertEquals(PAYMENT_APPLICATION_ACCOUNTING_SUBJECT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void relatedContractInvalidIdRejected() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setRelatedContractApplicationId(404L);
        when(contractMapper.selectById(404L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_RELATED_CONTRACT_INVALID.getCode(), ex.getCode());
        verify(mapper, never()).insert(any(FinancePaymentApplicationDO.class));
    }

    @Test
    void relatedContractPendingRejected() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setRelatedContractApplicationId(50L);
        when(contractMapper.selectById(50L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(50L)
                .approvalStatus(FinanceContractApprovalStatusEnum.PENDING.getStatus())
                .voided(false)
                .build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_RELATED_CONTRACT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void relatedContractApprovedUsesSettlement() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setRelatedContractApplicationId(51L);
        when(contractMapper.selectById(51L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(51L)
                .applicantUserId(1L)
                .fileType("付款业务合同")
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(false)
                .settlementMethod("月结30天")
                .build());
        service.createAndStart(req, 1L);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(51L, cap.getValue().getRelatedContractApplicationId());
        assertEquals("月结30天", cap.getValue().getContractSettlementMethod());
    }

    @Test
    void businessPaymentRequiresRelatedContract() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setRelatedContractApplicationId(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_RELATED_CONTRACT_REQUIRED.getCode(), ex.getCode());
        verify(mapper, never()).insert(any(FinancePaymentApplicationDO.class));
    }

    @Test
    void relatedContractMustBePaymentBusinessType() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        when(contractMapper.selectById(51L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(51L)
                .applicantUserId(1L)
                .fileType("销售合同")
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(false)
                .build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_RELATED_CONTRACT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void relatedContractRejectsNonOwner() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setRelatedContractApplicationId(52L);
        when(contractMapper.selectById(52L)).thenReturn(FinanceContractApplicationDO.builder()
                .id(52L)
                .applicantUserId(99L)
                .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                .voided(false)
                .build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_RELATED_CONTRACT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void onApprovalOutcomeIgnoresStaleProcessInstance() {
        when(mapper.selectById(11L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(11L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-new")
                .build());
        // 旧实例终态回调
        service.onApprovalOutcome(11L, "REJECTED", "pi-old");
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void dictInvalidFailsCreate() {
        when(dictDataApi.validateDictDataList(anyString(), anyCollection()))
                .thenThrow(new RuntimeException("bad dict"));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(baseReq(), 1L));
        assertEquals(PAYMENT_APPLICATION_DICT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void createWithProductTypeSoftwareSucceeds() {
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setCostProject("软件");
        Long id = service.createAndStart(req, 1L);
        assertEquals(100L, id);
        ArgumentCaptor<FinancePaymentApplicationDO> cap = ArgumentCaptor.forClass(FinancePaymentApplicationDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals("软件", cap.getValue().getCostProject());
        verify(dictDataApi).validateDictDataList(eq("finance_product_type"),
                argThat(values -> values.contains("软件")));
    }

    @Test
    void createWithOldCostProjectBaiduRechargeFails() {
        when(dictDataApi.validateDictDataList(eq("finance_product_type"),
                argThat(values -> values != null && values.contains("baidu_recharge"))))
                .thenThrow(new RuntimeException("bad dict"));
        FinancePaymentApplicationCreateAndStartReqVO req = baseReq();
        req.setCostProject("baidu_recharge");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndStart(req, 1L));
        assertEquals(PAYMENT_APPLICATION_DICT_INVALID.getCode(), ex.getCode());
        verify(mapper, never()).insert(any(FinancePaymentApplicationDO.class));
    }

    @Test
    void resubmitWithOldCostProjectBaiduRechargeFails() {
        when(mapper.selectById(81L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(81L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.REJECTED.getStatus())
                .applicationKind("ORDINARY")
                .voided(false)
                .costProject("baidu_recharge")
                .build());
        when(dictDataApi.validateDictDataList(eq("finance_product_type"),
                argThat(values -> values != null && values.contains("baidu_recharge"))))
                .thenThrow(new RuntimeException("bad dict"));
        FinancePaymentApplicationResubmitReqVO req = new FinancePaymentApplicationResubmitReqVO();
        req.setPaymentTiming(FinancePaymentTimingEnum.IMMEDIATE.getCode());
        req.setPaymentReason(FinancePaymentReasonEnum.BUSINESS.getCode());
        req.setPayeeCompanyId(9L);
        req.setEntityCompanyDeptId(20L);
        req.setApplyAmount(new BigDecimal("100.00"));
        req.setCurrency("CNY");
        req.setBusinessSettlementTerm("月结30天");
        req.setPayMethod("wire");
        req.setCostProject("baidu_recharge");
        req.setEvidenceFileUrls(List.of("https://x/a.pdf"));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.resubmit(81L, req, 1L));
        assertEquals(PAYMENT_APPLICATION_DICT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void getOldCostProjectRowDoesNotThrow() {
        when(mapper.selectById(82L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(82L)
                .costProject("baidu_recharge")
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .build());
        FinancePaymentApplicationDO row = assertDoesNotThrow(() -> service.getApplication(82L));
        assertEquals("baidu_recharge", row.getCostProject());
        verify(dictDataApi, never()).validateDictDataList(anyString(), anyCollection());
    }

    @Test
    void replayRejectsPaidOutcome() {
        when(mapper.selectById(12L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(12L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-12")
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.replayTerminalOutcome(12L, "PAID", "pi-12"));
        assertEquals(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID.getCode(), ex.getCode());
    }

    @Test
    void replayRejectsPiMismatch() {
        when(mapper.selectById(13L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(13L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-current")
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.replayTerminalOutcome(13L, "REJECTED", "pi-other"));
        assertEquals(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID.getCode(), ex.getCode());
    }

    @Test
    void replayRejectedUpdatesLedgerWhenHistoricEnded() {
        when(mapper.selectById(14L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(14L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-14")
                .build());
        when(mapper.update(isNull(), any())).thenReturn(1);
        org.flowable.engine.HistoryService historyService = mock(org.flowable.engine.HistoryService.class);
        org.flowable.engine.history.HistoricProcessInstanceQuery hq =
                mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        org.flowable.engine.history.HistoricProcessInstance hi =
                mock(org.flowable.engine.history.HistoricProcessInstance.class);
        when(historyProvider.getIfAvailable()).thenReturn(historyService);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hq);
        when(hq.processInstanceId("pi-14")).thenReturn(hq);
        when(hq.includeProcessVariables()).thenReturn(hq);
        when(hq.singleResult()).thenReturn(hi);
        when(hi.getEndTime()).thenReturn(new java.util.Date());
        when(hi.getProcessVariables()).thenReturn(java.util.Map.of(
                cn.iocoder.yudao.module.finance.framework.bpm.FinancePaymentApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE,
                cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.REJECT.getStatus()));
        service.replayTerminalOutcome(14L, "REJECTED", "pi-14");
        verify(mapper, atLeastOnce()).update(isNull(), any());
    }

    @Test
    void replayFailsWhenProcessStillRunning() {
        when(mapper.selectById(16L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(16L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-16")
                .build());
        org.flowable.engine.HistoryService historyService = mock(org.flowable.engine.HistoryService.class);
        org.flowable.engine.history.HistoricProcessInstanceQuery hq =
                mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        org.flowable.engine.history.HistoricProcessInstance hi =
                mock(org.flowable.engine.history.HistoricProcessInstance.class);
        when(historyProvider.getIfAvailable()).thenReturn(historyService);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hq);
        when(hq.processInstanceId("pi-16")).thenReturn(hq);
        when(hq.includeProcessVariables()).thenReturn(hq);
        when(hq.singleResult()).thenReturn(hi);
        when(hi.getEndTime()).thenReturn(null); // still running
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.replayTerminalOutcome(16L, "REJECTED", "pi-16"));
        assertEquals(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID.getCode(), ex.getCode());
        verify(mapper, never()).update(isNull(), any());
    }

    /** PAY-R16 推荐：历史已结束但缺 PROCESS_STATUS → fail-closed */
    @Test
    void replayFailsWhenHistoricEndedButProcessStatusMissing() {
        when(mapper.selectById(17L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(17L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-17")
                .build());
        org.flowable.engine.HistoryService historyService = mock(org.flowable.engine.HistoryService.class);
        org.flowable.engine.history.HistoricProcessInstanceQuery hq =
                mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        org.flowable.engine.history.HistoricProcessInstance hi =
                mock(org.flowable.engine.history.HistoricProcessInstance.class);
        when(historyProvider.getIfAvailable()).thenReturn(historyService);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hq);
        when(hq.processInstanceId("pi-17")).thenReturn(hq);
        when(hq.includeProcessVariables()).thenReturn(hq);
        when(hq.singleResult()).thenReturn(hi);
        when(hi.getEndTime()).thenReturn(new java.util.Date());
        when(hi.getProcessVariables()).thenReturn(java.util.Collections.emptyMap());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.replayTerminalOutcome(17L, "REJECTED", "pi-17"));
        assertEquals(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID.getCode(), ex.getCode());
        verify(mapper, never()).update(isNull(), any());
    }

    /** PAY-R16 推荐：历史 CANCEL 不可 replay 为 REJECTED */
    @Test
    void replayFailsWhenHistoricStatusDoesNotMatchOutcome() {
        when(mapper.selectById(18L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(18L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-18")
                .build());
        org.flowable.engine.HistoryService historyService = mock(org.flowable.engine.HistoryService.class);
        org.flowable.engine.history.HistoricProcessInstanceQuery hq =
                mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        org.flowable.engine.history.HistoricProcessInstance hi =
                mock(org.flowable.engine.history.HistoricProcessInstance.class);
        when(historyProvider.getIfAvailable()).thenReturn(historyService);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hq);
        when(hq.processInstanceId("pi-18")).thenReturn(hq);
        when(hq.includeProcessVariables()).thenReturn(hq);
        when(hq.singleResult()).thenReturn(hi);
        when(hi.getEndTime()).thenReturn(new java.util.Date());
        when(hi.getProcessVariables()).thenReturn(java.util.Map.of(
                cn.iocoder.yudao.module.finance.framework.bpm.FinancePaymentApprovalOutcomeDelegate.PROCESS_STATUS_VARIABLE,
                cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.CANCEL.getStatus()));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.replayTerminalOutcome(18L, "REJECTED", "pi-18"));
        assertEquals(PAYMENT_APPLICATION_APPROVAL_OUTCOME_INVALID.getCode(), ex.getCode());
        verify(mapper, never()).update(isNull(), any());
    }

    @Test
    void cancelWritesCancelledWhenNoProcess() {
        when(mapper.selectById(15L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(15L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId(null)
                .build());
        when(mapper.update(isNull(), any())).thenReturn(1);
        service.cancel(15L, 1L);
        verify(mapper, atLeastOnce()).update(isNull(), any());
        verify(processInstanceApi, never()).cancelProcessInstanceByStartUser(anyLong(), anyString(), anyString(), any());
    }

    /** PAY-R18：WAIT_PAY 不可撤 */
    @Test
    void cancelRejectsWhenWaitPay() {
        when(mapper.selectById(16L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(16L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-16")
                .build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.cancel(16L, 1L));
        assertEquals(PAYMENT_APPLICATION_STATUS_INVALID.getCode(), ex.getCode());
        verify(processInstanceApi, never()).cancelProcessInstanceByStartUser(anyLong(), anyString(), anyString(), any());
        verify(mapper, never()).update(isNull(), any());
    }

    /** PAY-R18：PENDING + PI 取消须带 taskCashier 禁止集 */
    @Test
    void cancelPendingWithProcessPassesCashierForbiddenKeys() {
        when(mapper.selectById(17L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(17L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-17")
                .build());
        when(processInstanceApi.cancelProcessInstanceByStartUser(anyLong(), anyString(), anyString(), any()))
                .thenReturn(CommonResult.success(true));
        when(mapper.update(isNull(), any())).thenReturn(1);

        service.cancel(17L, 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> keysCap = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(processInstanceApi).cancelProcessInstanceByStartUser(
                eq(1L), eq("pi-17"), anyString(), keysCap.capture());
        assertTrue(keysCap.getValue().contains(FinancePaymentApplicationService.TASK_CASHIER));
        verify(mapper, atLeastOnce()).update(isNull(), any());
    }

    /** PAY-R18：BPM 禁止集拒绝时不落 CANCELLED */
    @Test
    void cancelDoesNotWriteWhenBpmForbiddenRejects() {
        when(mapper.selectById(18L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(18L)
                .applicantUserId(1L)
                .status(FinancePaymentApplicationStatusEnum.PENDING.getStatus())
                .processInstanceId("pi-18")
                .build());
        when(processInstanceApi.cancelProcessInstanceByStartUser(anyLong(), anyString(), anyString(), any()))
                .thenReturn(CommonResult.error(1_009_004_009, "流程取消失败，当前活动任务已进入禁止取消的节点"));

        assertThrows(ServiceException.class, () -> service.cancel(18L, 1L));
        verify(mapper, never()).update(isNull(), any());
    }

    /**
     * G3：同键首次显式部分支付后，另有支付使「剩余」变化，再省略金额重放 → 归一化金额变化 → 冲突。
     */
    @Test
    void recordPayIdempotentOmitAmountAfterRemainingChangedConflicts() {
        LocalDate payDate = LocalDate.of(2026, 8, 10);
        when(mapper.selectByIdForUpdate(60L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(60L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-60")
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .build());
        // 本幂等键对应首次显式 50；另有 30 已付 → 排除本行后剩余 = 70；省略金额归一化为 70 ≠ 50
        when(payLineMapper.selectByAppAndIdempotencyKey(60L, "idem-partial")).thenReturn(
                cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.builder()
                        .id(1L).paymentApplicationId(60L).idempotencyKey("idem-partial")
                        .companyBankAccountId(7L)
                        .payAmount(new BigDecimal("50.00"))
                        .actualPayDate(payDate)
                        .payVoucherUrl("http://v-partial")
                        .build());
        when(payLineMapper.sumPayAmountByApplicationId(60L)).thenReturn(new BigDecimal("80.00"));

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(60L);
        req.setTaskId("t-60");
        req.setCompanyBankAccountId(7L);
        req.setIdempotencyKey("idem-partial");
        req.setActualPayDate(payDate);
        req.setPayVoucherUrl("http://v-partial");
        // 省略 payAmount
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_APPLICATION_IDEMPOTENCY_CONFLICT.getCode(), ex.getCode());
    }

    /**
     * G3：同键同归一化载荷（省略金额且剩余语义未变）重放成功。
     */
    @Test
    void recordPayIdempotentOmitAmountSameNormalizedReplayOk() {
        LocalDate payDate = LocalDate.of(2026, 8, 10);
        when(mapper.selectByIdForUpdate(61L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(61L)
                .status(FinancePaymentApplicationStatusEnum.PAID.getStatus())
                .processInstanceId("pi-61")
                .applyAmount(new BigDecimal("100.00"))
                .actualPayDate(payDate)
                .payVoucherUrl("http://v-full")
                .currency("CNY")
                .build());
        // 仅本行付清 100；省略金额 → remainingIfThisAbsent = 100
        when(payLineMapper.selectByAppAndIdempotencyKey(61L, "idem-full")).thenReturn(
                cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.builder()
                        .id(2L).paymentApplicationId(61L).idempotencyKey("idem-full")
                        .companyBankAccountId(7L)
                        .payAmount(new BigDecimal("100.00"))
                        .actualPayDate(payDate)
                        .payVoucherUrl("http://v-full")
                        .build());
        when(payLineMapper.sumPayAmountByApplicationId(61L)).thenReturn(new BigDecimal("100.00"));

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(61L);
        req.setTaskId("gone");
        req.setCompanyBankAccountId(7L);
        req.setIdempotencyKey("idem-full");
        req.setActualPayDate(payDate);
        req.setPayVoucherUrl("http://v-full");
        assertDoesNotThrow(() -> service.recordPay(req, 1L));
        verify(payLineMapper, never()).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
    }

    @Test
    void updateCurrentNodeFinanceDoesNotMarkWaitPay() {
        when(mapper.update(isNull(), any())).thenReturn(1);
        service.updateCurrentNode(37L, "finance", "待财务主管");
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        assertFalse(updateWritesStatus(cap.getValue(), "WAIT_PAY"), String.valueOf(cap.getValue().getSqlSet()));
        assertTrue(updateWritesStatus(cap.getValue(), "PENDING"),
                "财务复审未结束，必须保持审批中而不是待支付");
    }

    @Test
    void updateCurrentNodeTaskFinanceDoesNotMarkWaitPay() {
        when(mapper.update(isNull(), any())).thenReturn(1);
        service.updateCurrentNode(37L, "taskFinance", "财务复审");
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        assertFalse(updateWritesStatus(cap.getValue(), "WAIT_PAY"));
        assertTrue(updateWritesStatus(cap.getValue(), "PENDING"));
    }

    @Test
    void updateCurrentNodeCashierDoesNotMarkWaitPay() {
        when(mapper.update(isNull(), any())).thenReturn(1);
        service.updateCurrentNode(30L, "cashier", "待出纳");
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        assertFalse(updateWritesStatus(cap.getValue(), "WAIT_PAY"));
        assertTrue(updateWritesStatus(cap.getValue(), "PENDING"));
    }

    private static boolean updateWritesStatus(
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO> uw,
            String status) {
        String sqlSet = String.valueOf(uw.getSqlSet());
        if (sqlSet.contains("'" + status + "'") || sqlSet.contains('"' + status + '"')) {
            return true;
        }
        return uw.getParamNameValuePairs().values().stream().anyMatch(status::equals);
    }

    @Test
    void recordPayPartialSetsPartialPaidAndKeepsTaskOpen() {
        stubCashierTask("t-70", "pi-70", "1");
        stubPayAccount(77L, 20L);
        when(mapper.selectByIdForUpdate(70L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(70L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-70")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .applicationKind("ORDINARY")
                .build());
        when(payLineMapper.sumPayAmountByApplicationId(70L)).thenReturn(BigDecimal.ZERO);
        when(mapper.update(isNull(), any())).thenReturn(1);

        FinancePaymentRecordPayReqVO req = basePayReq(70L, "t-70", new BigDecimal("40.00"));
        service.recordPay(req, 1L);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        assertTrue(cap.getValue().getParamNameValuePairs().containsValue("PARTIAL_PAID"));
        verify(taskProvider.getIfAvailable(), never()).complete(anyString());
    }

    @Test
    void recordPayRejectsWhenConfirmedAmountWouldExceedApply() {
        stubCashierTask("t-71", "pi-71", "1");
        stubPayAccount(77L, 20L);
        when(mapper.selectByIdForUpdate(71L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(71L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-71")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .applicationKind("ORDINARY")
                .build());
        when(payLineMapper.sumPayAmountByApplicationId(71L)).thenReturn(new BigDecimal("80.00"));

        FinancePaymentRecordPayReqVO req = basePayReq(71L, "t-71", new BigDecimal("30.00"));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_APPLICATION_PAY_AMOUNT_INVALID.getCode(), ex.getCode());
        verify(payLineMapper, never()).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
    }

    @Test
    void recordPayAfterProcessEndedDoesNotRequireTaskId() {
        stubPayAccount(77L, 20L);
        org.flowable.engine.HistoryService historyService = mock(org.flowable.engine.HistoryService.class);
        org.flowable.engine.history.HistoricProcessInstanceQuery hq =
                mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        org.flowable.engine.history.HistoricProcessInstance hi =
                mock(org.flowable.engine.history.HistoricProcessInstance.class);
        when(historyProvider.getIfAvailable()).thenReturn(historyService);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hq);
        when(hq.processInstanceId("pi-72")).thenReturn(hq);
        when(hq.singleResult()).thenReturn(hi);
        when(hi.getEndTime()).thenReturn(new java.util.Date());
        when(mapper.selectByIdForUpdate(72L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(72L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-72")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .applicationKind("ORDINARY")
                .build());
        when(payLineMapper.sumPayAmountByApplicationId(72L)).thenReturn(BigDecimal.ZERO);
        when(mapper.update(isNull(), any())).thenReturn(1);

        FinancePaymentRecordPayReqVO req = basePayReq(72L, null, new BigDecimal("100.00"));
        assertDoesNotThrow(() -> service.recordPay(req, 1L));
        verify(payLineMapper).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
    }

    private void stubCashierTask(String taskId, String processInstanceId, String userId) {
        org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        org.flowable.task.api.TaskQuery tq = mock(org.flowable.task.api.TaskQuery.class);
        org.flowable.task.api.Task task = mock(org.flowable.task.api.Task.class);
        when(taskProvider.getIfAvailable()).thenReturn(taskService);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId(taskId)).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);
        when(task.getTaskDefinitionKey()).thenReturn("taskCashier");
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
        when(task.getId()).thenReturn(taskId);
        when(tq.taskCandidateOrAssigned(userId)).thenReturn(tq);
        when(tq.count()).thenReturn(1L);
    }

    private void stubPayAccount(Long accountId, Long entityCompanyDeptId) {
        var account = cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO.builder()
                .id(accountId).entityCompanyDeptId(entityCompanyDeptId).accountName("基本户").bankName("工行")
                .accountHolder("甲").accountNo("6222").currency("CNY").status(0).build();
        when(companyBankAccountService.get(accountId)).thenReturn(account);
        when(companyBankAccountService.requireEnabledForEntityCompany(eq(accountId), eq(entityCompanyDeptId)))
                .thenReturn(account);
    }

    @Test
    void recordPayBatchRejectsWhenLineSumWouldExceedRemaining() {
        stubPayAccount(77L, 20L);
        stubPayAccount(88L, 20L);
        when(mapper.selectByIdForUpdate(80L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(80L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-80")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .applicationKind("ORDINARY")
                .build());
        when(payLineMapper.sumPayAmountByApplicationId(80L)).thenReturn(BigDecimal.ZERO);

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(80L);
        req.setLines(List.of(
                payLine(77L, new BigDecimal("60.00"), "http://v1", "idem-80-a"),
                payLine(88L, new BigDecimal("50.00"), "http://v2", "idem-80-b")));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(PAYMENT_APPLICATION_PAY_AMOUNT_INVALID.getCode(), ex.getCode());
        verify(payLineMapper, never()).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
    }

    @Test
    void recordPayBatchPartialSetsPartialPaid() {
        stubPayAccount(77L, 20L);
        stubPayAccount(88L, 20L);
        when(mapper.selectByIdForUpdate(81L)).thenReturn(FinancePaymentApplicationDO.builder()
                .id(81L)
                .status(FinancePaymentApplicationStatusEnum.WAIT_PAY.getStatus())
                .processInstanceId("pi-81")
                .entityCompanyDeptId(20L)
                .applyAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .applicationKind("ORDINARY")
                .build());
        when(payLineMapper.sumPayAmountByApplicationId(81L)).thenReturn(BigDecimal.ZERO);
        when(mapper.update(isNull(), any())).thenReturn(1);

        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(81L);
        req.setLines(List.of(
                payLine(77L, new BigDecimal("40.00"), "http://v1", "idem-81-a"),
                payLine(88L, new BigDecimal("30.00"), "http://v2", "idem-81-b")));
        service.recordPay(req, 1L);

        verify(payLineMapper, times(2)).insert(any(cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO.class));
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<FinancePaymentApplicationDO>> cap =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        assertTrue(cap.getValue().getParamNameValuePairs().containsValue("PARTIAL_PAID"));
    }

    private static FinancePaymentRecordPayReqVO.Line payLine(
            Long accountId, BigDecimal amount, String voucher, String idem) {
        FinancePaymentRecordPayReqVO.Line line = new FinancePaymentRecordPayReqVO.Line();
        line.setCompanyBankAccountId(accountId);
        line.setPayAmount(amount);
        line.setActualPayDate(LocalDate.now());
        line.setPayVoucherUrl(voucher);
        line.setIdempotencyKey(idem);
        return line;
    }

    private static FinancePaymentRecordPayReqVO basePayReq(Long id, String taskId, BigDecimal amount) {
        FinancePaymentRecordPayReqVO req = new FinancePaymentRecordPayReqVO();
        req.setId(id);
        req.setTaskId(taskId);
        req.setCompanyBankAccountId(77L);
        req.setPayAmount(amount);
        req.setActualPayDate(LocalDate.now());
        req.setPayVoucherUrl("http://voucher");
        req.setIdempotencyKey("idem-" + id);
        return req;
    }

}
