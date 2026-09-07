package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceRedflushCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceRedflushDO;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceRedflushMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceInvoiceRedflushNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.framework.security.FinanceProcessParticipantSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_REDFUSH_AMOUNT_MISMATCH;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_REDFUSH_LOCK_FAILED;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_REDFUSH_PREDECESSOR_INVALID;
import static cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceRedflushService.PROCESS_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceInvoiceRedflushServiceImplTest {

    private FinanceInvoiceRedflushMapper redflushMapper;
    private FinanceInvoiceApplicationMapper applicationMapper;
    private FinanceInvoiceRedflushNoRedisDAO noRedisDAO;
    private FinanceBpmProcessInstanceApi processInstanceApi;
    private FinanceInvoiceApplicationService invoiceApplicationService;
    private FinanceInvoiceRedflushServiceImpl service;

    @BeforeEach
    void setUp() {
        redflushMapper = mock(FinanceInvoiceRedflushMapper.class);
        applicationMapper = mock(FinanceInvoiceApplicationMapper.class);
        noRedisDAO = mock(FinanceInvoiceRedflushNoRedisDAO.class);
        processInstanceApi = mock(FinanceBpmProcessInstanceApi.class);
        invoiceApplicationService = mock(FinanceInvoiceApplicationService.class);
        service = new FinanceInvoiceRedflushServiceImpl(
                redflushMapper, applicationMapper, noRedisDAO, processInstanceApi, invoiceApplicationService);
        SecurityFrameworkService security = mock(SecurityFrameworkService.class);
        when(security.hasPermission("finance:invoice-application:query")).thenReturn(true);
        ReflectionTestUtils.setField(service, "securityFrameworkService", security);
        ReflectionTestUtils.setField(service, "processParticipantSupport", mock(FinanceProcessParticipantSupport.class));
        when(noRedisDAO.generate(any(LocalDate.class))).thenReturn("IRF-20260824-1");
        when(processInstanceApi.createProcessInstance(anyLong(), any()))
                .thenReturn(CommonResult.success("pi-1"));
        doAnswer(inv -> {
            FinanceInvoiceRedflushDO row = inv.getArgument(0);
            row.setId(10L);
            return 1;
        }).when(redflushMapper).insert(any(FinanceInvoiceRedflushDO.class));
    }

    @Test
    void createCopiesAmountAndStartsWithoutOccupyCall() {
        when(applicationMapper.selectById(1L)).thenReturn(eligiblePredecessor());
        when(applicationMapper.tryLockForRedFlush(1L, 10L)).thenReturn(1);

        FinanceInvoiceRedflushCreateAndStartReqVO req = req(1L, "开错税号", new BigDecimal("100.00"));
        Long id = service.createAndStart(req, 200L);

        assertEquals(10L, id);
        ArgumentCaptor<FinanceInvoiceRedflushDO> cap = ArgumentCaptor.forClass(FinanceInvoiceRedflushDO.class);
        verify(redflushMapper).insert(cap.capture());
        assertEquals(new BigDecimal("100.00"), cap.getValue().getTotalAmount());
        verify(applicationMapper, never()).increasePendingClaimedAmount(anyLong(), any());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpm = ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(200L), bpm.capture());
        assertEquals(PROCESS_KEY, bpm.getValue().getProcessDefinitionKey());
        assertEquals("10", bpm.getValue().getBusinessKey());
    }

    @Test
    void createRejectsClientAmountMismatch() {
        when(applicationMapper.selectById(1L)).thenReturn(eligiblePredecessor());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(1L, "原因", new BigDecimal("99.00")), 200L));
        assertEquals(INVOICE_REDFUSH_AMOUNT_MISMATCH.getCode(), ex.getCode());
        verify(redflushMapper, never()).insert(any(FinanceInvoiceRedflushDO.class));
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
    }

    @Test
    void createRejectsClaimedPredecessor() {
        FinanceInvoiceApplicationDO claimed = eligiblePredecessor();
        claimed.setConfirmedClaimedAmount(new BigDecimal("1"));
        when(applicationMapper.selectById(1L)).thenReturn(claimed);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(1L, "原因", null), 200L));
        assertEquals(INVOICE_REDFUSH_PREDECESSOR_INVALID.getCode(), ex.getCode());
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
    }

    @Test
    void concurrentSecondCreateFails() {
        when(applicationMapper.selectById(1L)).thenReturn(eligiblePredecessor());
        when(applicationMapper.tryLockForRedFlush(1L, 10L)).thenReturn(0);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.createAndStart(req(1L, "原因", null), 200L));
        assertEquals(INVOICE_REDFUSH_LOCK_FAILED.getCode(), ex.getCode());
        verify(processInstanceApi, never()).createProcessInstance(anyLong(), any());
    }

    @Test
    void rejectUnlocksAndDoesNotTouchOccupy() {
        when(redflushMapper.selectById(10L)).thenReturn(FinanceInvoiceRedflushDO.builder()
                .id(10L)
                .predecessorApplicationId(1L)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus())
                .build());
        service.onApprovalOutcome(10L, "REJECTED");
        verify(applicationMapper).unlockRedFlush(1L, 10L);
        verify(applicationMapper, never()).tryLockForRedFlush(anyLong(), anyLong());
        verify(invoiceApplicationService, never()).releaseOccupyForRedFlush(anyLong());
    }

    @Test
    void approveDoesNotReleaseOccupy() {
        when(redflushMapper.selectById(10L)).thenReturn(FinanceInvoiceRedflushDO.builder()
                .id(10L)
                .predecessorApplicationId(1L)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.PENDING.getStatus())
                .build());
        service.onApprovalOutcome(10L, "APPROVED");
        verify(invoiceApplicationService, never()).releaseOccupyForRedFlush(anyLong());
        verify(applicationMapper, never()).unlockRedFlush(anyLong(), anyLong());
    }

    @Test
    void completeIssueReleasesPredecessorOccupyOnce() {
        when(redflushMapper.selectById(10L)).thenReturn(FinanceInvoiceRedflushDO.builder()
                .id(10L)
                .predecessorApplicationId(1L)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus())
                .voided(Boolean.FALSE)
                .build());
        FinanceInvoiceApplicationCompleteIssueReqVO req = new FinanceInvoiceApplicationCompleteIssueReqVO();
        req.setApplicationId(10L);
        FinanceInvoiceApplicationCompleteIssueReqVO.FileItem file = new FinanceInvoiceApplicationCompleteIssueReqVO.FileItem();
        file.setUrl("http://x/a.pdf");
        req.setFiles(List.of(file));
        service.completeIssue(10L, req);
        verify(invoiceApplicationService).releaseOccupyForRedFlush(1L);
        when(redflushMapper.selectById(10L)).thenReturn(FinanceInvoiceRedflushDO.builder()
                .id(10L)
                .predecessorApplicationId(1L)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.FULL.getStatus())
                .voided(Boolean.FALSE)
                .build());
        service.completeIssue(10L, req);
        verify(invoiceApplicationService, times(1)).releaseOccupyForRedFlush(1L);
    }

    private static FinanceInvoiceRedflushCreateAndStartReqVO req(Long predId, String reason, BigDecimal amount) {
        FinanceInvoiceRedflushCreateAndStartReqVO vo = new FinanceInvoiceRedflushCreateAndStartReqVO();
        vo.setPredecessorApplicationId(predId);
        vo.setReason(reason);
        vo.setTotalAmount(amount);
        return vo;
    }

    private static FinanceInvoiceApplicationDO eligiblePredecessor() {
        return FinanceInvoiceApplicationDO.builder()
                .id(1L)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.FULL.getStatus())
                .voided(Boolean.FALSE)
                .redFlushed(Boolean.FALSE)
                .totalAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .pendingClaimedAmount(BigDecimal.ZERO)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .build();
    }
}
