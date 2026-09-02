package cn.iocoder.yudao.module.finance.service.expense;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseApproveReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseRecordPayReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementCreateReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementLineReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_APPROVED_AMOUNT_INVALID;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_LINE_KIND_MISMATCH;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_INVOICE_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PAY_ACCOUNT_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_PREDOC_OCCUPIED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_FIELD_REQUIRED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_EXTRA_ATTACHMENTS_EXCEED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_ACCESS_DENIED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_LINES_EMPTY;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.EXPENSE_REIMBURSEMENT_STATUS_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceExpenseReimbursementServiceImplTest {

    private FinanceExpenseReimbursementMapper mapper;
    private FinanceExpenseReimbursementLineMapper lineMapper;
    private FinanceExpensePredocService predoc;
    private FinanceBpmProcessInstanceApi bpm;
    private FinanceExpenseReimbursementServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceExpenseReimbursementMapper.class);
        lineMapper = mock(FinanceExpenseReimbursementLineMapper.class);
        AdminUserApi users = mock(AdminUserApi.class);
        bpm = mock(FinanceBpmProcessInstanceApi.class);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(1L);
        user.setDeptId(10L);
        user.setNickname("张三");
        when(users.getUser(anyLong())).thenReturn(CommonResult.success(user));
        CommonResult<String> pi = mock(CommonResult.class);
        when(pi.getCheckedData()).thenReturn("proc-1");
        when(bpm.createProcessInstance(anyLong(), any())).thenReturn(pi);
        doAnswer(inv -> {
            FinanceExpenseReimbursementDO row = inv.getArgument(0);
            row.setId(88L);
            return 1;
        }).when(mapper).insert(any(FinanceExpenseReimbursementDO.class));
        predoc = mock(FinanceExpensePredocService.class);
        when(predoc.isApprovedTrip(anyLong(), any())).thenReturn(true);
        when(predoc.isApprovedOuting(anyLong(), any())).thenReturn(true);
        when(predoc.resolveStay(anyLong(), any(), any())).thenReturn(
                new FinanceExpensePredocService.StayStay("杭州",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2),
                        1L, List.of()));
        cn.iocoder.yudao.module.system.api.dept.DeptApi deptApi =
                mock(cn.iocoder.yudao.module.system.api.dept.DeptApi.class);
        cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO company =
                new cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO();
        company.setId(10L);
        company.setName("测试公司");
        company.setOrgType("1");
        when(deptApi.getDept(10L)).thenReturn(CommonResult.success(company));
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.flowable.engine.TaskService> taskServiceProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(taskServiceProvider.getIfAvailable()).thenReturn(null);
        cn.iocoder.yudao.module.finance.dal.redis.no.FinanceExpenseReimbursementNoRedisDAO noDao =
                mock(cn.iocoder.yudao.module.finance.dal.redis.no.FinanceExpenseReimbursementNoRedisDAO.class);
        when(noDao.generate(any(LocalDate.class))).thenReturn("EXP-20260831-1");
        service = new FinanceExpenseReimbursementServiceImpl(mapper, lineMapper, users, bpm,
                mock(cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService.class),
                predoc, deptApi, taskServiceProvider, noDao);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.flowable.engine.HistoryService> historyProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(historyProvider.getIfAvailable()).thenReturn(null);
        ReflectionTestUtils.setField(service, "historyServiceProvider", historyProvider);
        cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport participant =
                mock(cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport.class);
        when(participant.canReadBill(any(), any(), any())).thenAnswer(inv -> {
            Long userId = inv.getArgument(0);
            Long applicantId = inv.getArgument(1);
            return userId != null && userId.equals(applicantId);
        });
        ReflectionTestUtils.setField(service, "processParticipantSupport", participant);
    }

    @Test
    void createSumsApplyAmountAndStartsProcess() {
        Long id = service.create(baseReq(false), 1L);
        assertEquals(88L, id);
        ArgumentCaptor<FinanceExpenseReimbursementDO> cap =
                ArgumentCaptor.forClass(FinanceExpenseReimbursementDO.class);
        verify(mapper).insert(cap.capture());
        assertEquals(new BigDecimal("30.00"), cap.getValue().getApplyAmount());
        assertEquals("工商银行", cap.getValue().getPayeeBankName());
        assertEquals("【报销】-张三-2026-08-30.00", cap.getValue().getProcessTitle());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCap =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(bpm).createProcessInstance(anyLong(), bpmCap.capture());
        assertEquals(cap.getValue().getApplicationNo(), bpmCap.getValue().getVariables().get("billCode"));
        assertEquals(cap.getValue().getApplicationNo(), bpmCap.getValue().getVariables().get("applicationNo"));
        org.junit.jupiter.api.Assertions.assertNotNull(cap.getValue().getApplicationNo());
        org.junit.jupiter.api.Assertions.assertTrue(cap.getValue().getApplicationNo().startsWith("EXP-"));
        verify(mapper).updateById(any(FinanceExpenseReimbursementDO.class));
    }

    @Test
    void missingPayeeBankNameRejected() {
        FinanceExpenseReimbursementCreateReqVO req = baseReq(false);
        req.setPayeeBankName("  ");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_FIELD_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void emptyLinesRejected() {
        FinanceExpenseReimbursementCreateReqVO req = baseReq(false);
        req.setLines(List.of());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_LINES_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void proxyRejectsNormalLines() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(baseReq(true), 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_LINE_KIND_MISMATCH.getCode(), ex.getCode());
    }

    @Test
    void approveRejectsAmountOverApply() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).applyAmount(new BigDecimal("30.00"))
                .status(FinanceExpenseReimbursementDO.STATUS_PENDING).build());
        FinanceExpenseApproveReqVO req = new FinanceExpenseApproveReqVO();
        req.setId(88L);
        req.setApprovedAmount(new BigDecimal("40"));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.approve(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_APPROVED_AMOUNT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void recordPayRequiresCompanyAccount() {
        FinanceExpenseRecordPayReqVO req = new FinanceExpenseRecordPayReqVO();
        req.setId(88L);
        req.setActualPayDate(LocalDate.of(2026, 8, 20));
        req.setPayVoucherUrl("https://x/v.pdf");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_PAY_ACCOUNT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void recordPayRejectsWhenProcessStillRunning() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L)
                .status(FinanceExpenseReimbursementDO.STATUS_WAIT_PAY)
                .processInstanceId("pi-running")
                .build());
        org.flowable.engine.HistoryService historyService = mock(org.flowable.engine.HistoryService.class);
        org.flowable.engine.history.HistoricProcessInstanceQuery hq =
                mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.flowable.engine.HistoryService> historyProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(historyProvider.getIfAvailable()).thenReturn(historyService);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hq);
        when(hq.processInstanceIds(any())).thenReturn(hq);
        when(hq.finished()).thenReturn(hq);
        when(hq.list()).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "historyServiceProvider", historyProvider);

        FinanceExpenseRecordPayReqVO req = new FinanceExpenseRecordPayReqVO();
        req.setId(88L);
        req.setCompanyBankAccountId(1L);
        req.setActualPayDate(LocalDate.of(2026, 8, 20));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.recordPay(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void withInvoiceRejectsMissingInvoice() {
        FinanceExpenseReimbursementCreateReqVO req = baseReq(false);
        req.getLines().get(0).setInvoiceFileUrl(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_INVOICE_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void getReturnsPredocFieldsAndResolvedBillPk() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).applicantUserId(1L).processInstanceId("exp-pi")
                .payeeAccountNo("622200001111").build());
        FinanceExpenseReimbursementLineDO line = FinanceExpenseReimbursementLineDO.builder()
                .lineKind(FinanceExpenseReimbursementLineDO.KIND_NORMAL)
                .category("travel")
                .predocType("TRIP")
                .predocProcessInstanceId("trip-pi")
                .amount(new BigDecimal("10"))
                .build();
        when(lineMapper.selectByReimbursementId(88L)).thenReturn(List.of(line));
        when(predoc.resolveBillPk("TRIP", "trip-pi")).thenReturn(42L);

        var vo = service.get(88L, 1L, false);
        assertEquals("TRIP", vo.getLines().get(0).getPredocType());
        assertEquals("trip-pi", vo.getLines().get(0).getPredocProcessInstanceId());
        assertEquals(42L, vo.getLines().get(0).getPredocBillId());
    }

    @Test
    void historicAssigneeCanGetBill() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).applicantUserId(1L).processInstanceId("exp-pi")
                .payeeAccountNo("622200001111").build());
        when(lineMapper.selectByReimbursementId(88L)).thenReturn(List.of());
        when(predoc.isProcessAssignee("exp-pi", 9L)).thenReturn(true);

        var vo = service.get(88L, 9L, false);
        assertEquals(88L, vo.getId());
    }

    @Test
    void strangerWithoutQueryOrTaskIsAccessDenied() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).applicantUserId(1L).processInstanceId("exp-pi").build());
        when(predoc.isProcessAssignee("exp-pi", 9L)).thenReturn(false);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.get(88L, 9L, false));
        assertEquals(EXPENSE_REIMBURSEMENT_ACCESS_DENIED.getCode(), ex.getCode());
    }

    @Test
    void travelRequiresTripPredoc() {
        FinanceExpenseReimbursementCreateReqVO req = baseReq(false);
        req.getLines().get(0).setCategory("travel");
        req.getLines().get(0).setPredocType(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_PREDOC_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void extraAttachmentsOverThirtyRejected() {
        FinanceExpenseReimbursementCreateReqVO req = baseReq(false);
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        for (int i = 0; i < 31; i++) {
            urls.add("https://files.example/a" + i + ".pdf");
        }
        req.setExtraAttachments(urls);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_EXTRA_ATTACHMENTS_EXCEED.getCode(), ex.getCode());
    }

    @Test
    void extraAttachmentsDedupesRepeatedUrls() {
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        for (int i = 0; i < 16; i++) {
            urls.add("https://files.example/a" + i + ".pdf");
        }
        urls.addAll(new java.util.ArrayList<>(urls));
        java.util.List<String> out =
                FinanceExpenseReimbursementServiceImpl.normalizeExtraAttachments(urls);
        assertEquals(16, out.size());
        assertEquals("https://files.example/a0.pdf", out.get(0));
        assertEquals("https://files.example/a15.pdf", out.get(15));
    }

    @Test
    void onApprovalOutcomeRejectsPendingToRejected() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).status(FinanceExpenseReimbursementDO.STATUS_PENDING)
                .processInstanceId("pi-1").build());
        service.onApprovalOutcome(88L, FinanceExpenseReimbursementDO.STATUS_REJECTED, "pi-1");
        ArgumentCaptor<FinanceExpenseReimbursementDO> cap =
                ArgumentCaptor.forClass(FinanceExpenseReimbursementDO.class);
        verify(mapper).updateById(cap.capture());
        assertEquals(FinanceExpenseReimbursementDO.STATUS_REJECTED, cap.getValue().getStatus());
    }

    @Test
    void onApprovalOutcomeCancelPendingToCancelled() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).status(FinanceExpenseReimbursementDO.STATUS_PENDING)
                .processInstanceId("pi-2").build());
        service.onApprovalOutcome(88L, FinanceExpenseReimbursementDO.STATUS_CANCELLED, "pi-2");
        ArgumentCaptor<FinanceExpenseReimbursementDO> cap =
                ArgumentCaptor.forClass(FinanceExpenseReimbursementDO.class);
        verify(mapper).updateById(cap.capture());
        assertEquals(FinanceExpenseReimbursementDO.STATUS_CANCELLED, cap.getValue().getStatus());
    }

    @Test
    void onApprovalOutcomeRejectedIsIdempotent() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).status(FinanceExpenseReimbursementDO.STATUS_REJECTED)
                .processInstanceId("pi-1").build());
        service.onApprovalOutcome(88L, FinanceExpenseReimbursementDO.STATUS_REJECTED, "pi-1");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .updateById(any(FinanceExpenseReimbursementDO.class));
    }

    @Test
    void occupiedTripBlocksAnotherReimbursement() {
        when(lineMapper.existsOccupiedPredoc("trip-1")).thenReturn(true);
        FinanceExpenseReimbursementCreateReqVO req = travelReq("trip-1");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req, 1L));
        assertEquals(EXPENSE_REIMBURSEMENT_PREDOC_OCCUPIED.getCode(), ex.getCode());
    }

    @Test
    void sameBillTwoLinesSameTripSucceeds() {
        when(lineMapper.existsOccupiedPredoc("trip-1")).thenReturn(false);
        FinanceExpenseReimbursementCreateReqVO req = travelReq("trip-1");
        FinanceExpenseReimbursementLineReqVO line2 = travelLine("trip-1");
        line2.setAmount(new BigDecimal("20"));
        line2.setInvoiceFileUrl("https://files.example/inv-t2.jpg");
        req.setLines(List.of(req.getLines().get(0), line2));
        assertEquals(88L, service.create(req, 1L));
        org.mockito.Mockito.verify(lineMapper, org.mockito.Mockito.times(1)).existsOccupiedPredoc("trip-1");
    }

    @Test
    void occupiedHeaderSqlIncludesOccupyStatusesAndExcludesReleased() {
        String sql = FinanceExpenseReimbursementLineMapper.OCCUPIED_HEADER_IDS_SQL;
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("'PENDING'"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("'WAIT_PAY'"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("'PAID'"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("'REJECTED'"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("'CANCELLED'"));
    }

    @Test
    void rejectedOccupantDoesNotBlock() {
        when(lineMapper.existsOccupiedPredoc("trip-1")).thenReturn(false);
        assertEquals(88L, service.create(travelReq("trip-1"), 1L));
    }

    @Test
    void onApprovalOutcomeIgnoresStaleProcessInstance() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).status(FinanceExpenseReimbursementDO.STATUS_PENDING)
                .processInstanceId("pi-new").build());
        service.onApprovalOutcome(88L, FinanceExpenseReimbursementDO.STATUS_REJECTED, "pi-old");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .updateById(any(FinanceExpenseReimbursementDO.class));
    }

    @Test
    void listOccupiedPredocIdsDelegatesToMapper() {
        when(lineMapper.selectOccupiedPredocProcessInstanceIds()).thenReturn(List.of("trip-1"));
        assertEquals(List.of("trip-1"), service.listOccupiedPredocProcessInstanceIds());
    }

    @Test
    void onApprovalOutcomePaidStaysPaidOnReject() {
        when(mapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).status(FinanceExpenseReimbursementDO.STATUS_PAID)
                .processInstanceId("pi-3").build());
        service.onApprovalOutcome(88L, FinanceExpenseReimbursementDO.STATUS_REJECTED, "pi-3");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .updateById(any(FinanceExpenseReimbursementDO.class));
    }

    private static FinanceExpenseReimbursementCreateReqVO baseReq(boolean proxy) {
        FinanceExpenseReimbursementLineReqVO line = new FinanceExpenseReimbursementLineReqVO();
        line.setLineKind(FinanceExpenseReimbursementLineDO.KIND_NORMAL);
        line.setCategory("office");
        line.setFeeDate(LocalDate.of(2026, 8, 1));
        line.setAmount(new BigDecimal("10"));
        line.setInvoiceFileUrl("https://files.example/inv1.jpg");
        FinanceExpenseReimbursementLineReqVO line2 = new FinanceExpenseReimbursementLineReqVO();
        line2.setLineKind(FinanceExpenseReimbursementLineDO.KIND_NORMAL);
        line2.setCategory("office");
        line2.setFeeDate(LocalDate.of(2026, 8, 2));
        line2.setAmount(new BigDecimal("20"));
        line2.setInvoiceFileUrl("https://files.example/inv2.jpg");
        FinanceExpenseReimbursementCreateReqVO req = new FinanceExpenseReimbursementCreateReqVO();
        req.setPeriodLabel("2026-08");
        req.setProxyTicket(proxy);
        req.setPayeeAccountName("张三");
        req.setPayeeBankName("工商银行");
        req.setPayeeAccountNo("622200001111");
        req.setLines(List.of(line, line2));
        return req;
    }

    private static FinanceExpenseReimbursementLineReqVO travelLine(String tripPi) {
        FinanceExpenseReimbursementLineReqVO line = new FinanceExpenseReimbursementLineReqVO();
        line.setLineKind(FinanceExpenseReimbursementLineDO.KIND_NORMAL);
        line.setCategory("travel");
        line.setFeeDate(LocalDate.of(2026, 8, 1));
        line.setAmount(new BigDecimal("10"));
        line.setInvoiceFileUrl("https://files.example/inv-t.jpg");
        line.setPredocType("TRIP");
        line.setPredocProcessInstanceId(tripPi);
        return line;
    }

    private static FinanceExpenseReimbursementCreateReqVO travelReq(String tripPi) {
        FinanceExpenseReimbursementCreateReqVO req = new FinanceExpenseReimbursementCreateReqVO();
        req.setPeriodLabel("2026-08");
        req.setProxyTicket(false);
        req.setPayeeAccountName("张三");
        req.setPayeeBankName("工商银行");
        req.setPayeeAccountNo("622200001111");
        req.setLines(List.of(travelLine(tripPi)));
        return req;
    }
}
