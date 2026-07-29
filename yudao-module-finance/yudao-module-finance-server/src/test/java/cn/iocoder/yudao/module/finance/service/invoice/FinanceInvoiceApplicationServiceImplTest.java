package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceInvoiceApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_OCCUPY_CONCURRENT;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_OCCUPY_EXCEED;
import static cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationServiceImpl.PROCESS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * createAndStart：占用失败零副作用、成功启流回写、并发 CAS 失败。
 */
class FinanceInvoiceApplicationServiceImplTest {

    private FinanceInvoiceApplicationMapper applicationMapper;
    private FinanceInvoiceApplicationLineMapper lineMapper;
    private FinanceBusinessOrderMapper businessOrderMapper;
    private FinanceInvoiceApplicationNoRedisDAO applicationNoRedisDAO;
    private BpmProcessInstanceApi processInstanceApi;
    private FinanceInvoiceApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(FinanceInvoiceApplicationMapper.class);
        lineMapper = mock(FinanceInvoiceApplicationLineMapper.class);
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        applicationNoRedisDAO = mock(FinanceInvoiceApplicationNoRedisDAO.class);
        processInstanceApi = mock(BpmProcessInstanceApi.class);
        service = new FinanceInvoiceApplicationServiceImpl(applicationMapper, lineMapper, businessOrderMapper,
                applicationNoRedisDAO, processInstanceApi);

        when(applicationNoRedisDAO.generate(any(LocalDate.class))).thenReturn("INV-20260729-1");
        doAnswer(invocation -> {
            FinanceInvoiceApplicationDO app = invocation.getArgument(0);
            app.setId(100L);
            return 1;
        }).when(applicationMapper).insert(any(FinanceInvoiceApplicationDO.class));
        doAnswer(invocation -> {
            FinanceInvoiceApplicationLineDO line = invocation.getArgument(0);
            line.setId(1L);
            return 1;
        }).when(lineMapper).insert(any(FinanceInvoiceApplicationLineDO.class));
    }

    @Test
    void createAndStartShouldFailOnExcessOccupyWithoutSideEffects() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "80.00")));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(line(10L, "50.00")), 200L));

        assertEquals(INVOICE_APPLICATION_OCCUPY_EXCEED.getCode(), ex.getCode());
        verify(applicationMapper, never()).insert(any(FinanceInvoiceApplicationDO.class));
        verify(lineMapper, never()).insert(any(FinanceInvoiceApplicationLineDO.class));
        verify(businessOrderMapper, never()).increaseInvoicedOccupiedAmount(anyLong(), any());
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void createAndStartShouldOccupyAndWriteProcessInstanceId() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "200.00", "20.00"),
                order(11L, "100.00", "0.00")));
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("80.00")))).thenReturn(1);
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(eq(11L), eq(new BigDecimal("30.00")))).thenReturn(1);
        when(processInstanceApi.createProcessInstance(eq(200L), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn(CommonResult.success("proc-xyz"));

        Long appId = service.createAndStart(req(
                line(10L, "50.00"),
                line(10L, "30.00"),
                line(11L, "30.00")), 200L);

        assertEquals(100L, appId);

        ArgumentCaptor<FinanceInvoiceApplicationDO> appCaptor = ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).insert(appCaptor.capture());
        FinanceInvoiceApplicationDO inserted = appCaptor.getValue();
        assertEquals("INV-20260729-1", inserted.getApplicationNo());
        assertEquals(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus(), inserted.getApprovalStatus());
        assertEquals(FinanceInvoiceIssueStatusEnum.NONE.getStatus(), inserted.getIssueStatus());
        assertEquals(0, inserted.getTotalAmount().compareTo(new BigDecimal("110.00")));
        assertEquals(0, inserted.getConfirmedClaimedAmount().compareTo(new BigDecimal("0.00")));
        assertEquals(0, inserted.getPendingClaimedAmount().compareTo(new BigDecimal("0.00")));
        assertEquals(Boolean.FALSE, inserted.getVoided());
        assertEquals(200L, inserted.getApplicantUserId());
        assertEquals("购方A", inserted.getBuyerName());

        verify(lineMapper, times(3)).insert(any(FinanceInvoiceApplicationLineDO.class));
        // 同 BO 明细汇总后一次占用
        verify(businessOrderMapper).increaseInvoicedOccupiedAmount(10L, new BigDecimal("80.00"));
        verify(businessOrderMapper).increaseInvoicedOccupiedAmount(11L, new BigDecimal("30.00"));

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(200L), bpmCaptor.capture());
        BpmProcessInstanceCreateReqDTO bpmReq = bpmCaptor.getValue();
        assertEquals(PROCESS_KEY, bpmReq.getProcessDefinitionKey());
        assertEquals("100", bpmReq.getBusinessKey());
        assertEquals(new BigDecimal("110.00"), bpmReq.getVariables().get("totalAmount"));
        assertEquals("购方A", bpmReq.getVariables().get("buyerName"));
        assertEquals(200L, bpmReq.getVariables().get("applicantUserId"));
        assertEquals("INV-20260729-1", bpmReq.getVariables().get("applicationNo"));

        ArgumentCaptor<FinanceInvoiceApplicationDO> updateCaptor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).updateById(updateCaptor.capture());
        assertEquals(100L, updateCaptor.getValue().getId());
        assertEquals("proc-xyz", updateCaptor.getValue().getProcessInstanceId());
    }

    @Test
    void createAndStartShouldFailWhenConcurrentOccupyReturnsZero() {
        when(businessOrderMapper.selectListByIds(any())).thenReturn(List.of(
                order(10L, "100.00", "0.00")));
        // 预检通过，CAS 返回 0（并发第二单）
        when(businessOrderMapper.increaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("60.00")))).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(line(10L, "60.00")), 200L));

        assertEquals(INVOICE_APPLICATION_OCCUPY_CONCURRENT.getCode(), ex.getCode());
        verify(applicationMapper).insert(any(FinanceInvoiceApplicationDO.class));
        verify(lineMapper).insert(any(FinanceInvoiceApplicationLineDO.class));
        verify(businessOrderMapper).increaseInvoicedOccupiedAmount(10L, new BigDecimal("60.00"));
        verifyNoInteractions(processInstanceApi);
        verify(applicationMapper, never()).updateById(any(FinanceInvoiceApplicationDO.class));
    }

    private static FinanceInvoiceApplicationCreateAndStartReqVO req(
            FinanceInvoiceApplicationCreateAndStartReqVO.Line... lines) {
        FinanceInvoiceApplicationCreateAndStartReqVO reqVO = new FinanceInvoiceApplicationCreateAndStartReqVO();
        reqVO.setBuyerName("购方A");
        reqVO.setInvoiceCompany("开票公司");
        reqVO.setInvoiceType("专票");
        reqVO.setLines(List.of(lines));
        return reqVO;
    }

    private static FinanceInvoiceApplicationCreateAndStartReqVO.Line line(Long businessOrderId, String amount) {
        FinanceInvoiceApplicationCreateAndStartReqVO.Line line =
                new FinanceInvoiceApplicationCreateAndStartReqVO.Line();
        line.setBusinessOrderId(businessOrderId);
        line.setAmount(new BigDecimal(amount));
        return line;
    }

    private static FinanceBusinessOrderDO order(Long id, String settlement, String occupied) {
        return FinanceBusinessOrderDO.builder()
                .id(id)
                .settlementAmount(new BigDecimal(settlement))
                .invoicedOccupiedAmount(new BigDecimal(occupied))
                .build();
    }

    // ---------- T3 / T4 ----------

    @Test
    void onApprovalOutcomeApprovedShouldNotReleaseOccupy() {
        when(applicationMapper.selectById(100L)).thenReturn(pendingApp(100L));
        service.onApprovalOutcome(100L, "APPROVED");
        ArgumentCaptor<FinanceInvoiceApplicationDO> captor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).updateById(captor.capture());
        assertEquals(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus(),
                captor.getValue().getApprovalStatus());
        verify(businessOrderMapper, never()).decreaseInvoicedOccupiedAmount(anyLong(), any());
    }

    @Test
    void onApprovalOutcomeRejectedShouldReleaseOccupy() {
        when(applicationMapper.selectById(100L)).thenReturn(pendingApp(100L));
        when(lineMapper.selectListByApplicationId(100L)).thenReturn(List.of(
                FinanceInvoiceApplicationLineDO.builder().id(1L).applicationId(100L)
                        .businessOrderId(10L).amount(new BigDecimal("50.00")).build()));
        when(businessOrderMapper.decreaseInvoicedOccupiedAmount(eq(10L), eq(new BigDecimal("50.00"))))
                .thenReturn(1);

        service.onApprovalOutcome(100L, "REJECTED");

        verify(businessOrderMapper).decreaseInvoicedOccupiedAmount(10L, new BigDecimal("50.00"));
        ArgumentCaptor<FinanceInvoiceApplicationDO> captor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).updateById(captor.capture());
        assertEquals(FinanceInvoiceApprovalStatusEnum.REJECTED.getStatus(),
                captor.getValue().getApprovalStatus());
    }

    @Test
    void onApprovalOutcomeIdempotentSameOutcome() {
        FinanceInvoiceApplicationDO approved = pendingApp(100L);
        approved.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus());
        when(applicationMapper.selectById(100L)).thenReturn(approved);
        service.onApprovalOutcome(100L, "APPROVED");
        verify(applicationMapper, never()).updateById(any(FinanceInvoiceApplicationDO.class));
        verify(businessOrderMapper, never()).decreaseInvoicedOccupiedAmount(anyLong(), any());
    }

    @Test
    void updateIssueProgressShouldRejectWhenNotApproved() {
        FinanceInvoiceApplicationDO app = pendingApp(100L);
        when(applicationMapper.selectById(100L)).thenReturn(app);
        cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO();
        req.setApplicationId(100L);
        req.setLineId(1L);
        req.setInvoiceNo("INV-NO-1");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateIssueProgress(req));
        assertEquals(cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_ISSUE_NOT_ALLOWED.getCode(),
                ex.getCode());
    }

    @Test
    void updateIssueProgressPartialThenFull() {
        FinanceInvoiceApplicationDO app = pendingApp(100L);
        app.setApprovalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus());
        when(applicationMapper.selectById(100L)).thenReturn(app);
        FinanceInvoiceApplicationLineDO line1 = FinanceInvoiceApplicationLineDO.builder()
                .id(1L).applicationId(100L).businessOrderId(10L).amount(new BigDecimal("10"))
                .issueStatus(0).build();
        FinanceInvoiceApplicationLineDO line2 = FinanceInvoiceApplicationLineDO.builder()
                .id(2L).applicationId(100L).businessOrderId(11L).amount(new BigDecimal("20"))
                .issueStatus(0).build();
        when(lineMapper.selectById(1L)).thenReturn(line1);
        when(lineMapper.selectListByApplicationId(100L)).thenReturn(List.of(line1, line2));

        cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO req =
                new cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO();
        req.setApplicationId(100L);
        req.setLineId(1L);
        req.setInvoiceNo("NO-1");
        service.updateIssueProgress(req);

        ArgumentCaptor<FinanceInvoiceApplicationDO> appCaptor =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).updateById(appCaptor.capture());
        assertEquals(FinanceInvoiceIssueStatusEnum.PARTIAL.getStatus(), appCaptor.getValue().getIssueStatus());
    }

    private static FinanceInvoiceApplicationDO pendingApp(Long id) {
        return FinanceInvoiceApplicationDO.builder()
                .id(id)
                .applicationNo("INV-1")
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                .totalAmount(new BigDecimal("50.00"))
                .confirmedClaimedAmount(ZERO)
                .pendingClaimedAmount(ZERO)
                .applicantUserId(200L)
                .voided(Boolean.FALSE)
                .build();
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

}
