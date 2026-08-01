package cn.iocoder.yudao.module.finance.service.receipt;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptLifecycleAuditDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceBankReceiptMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceReceiptLifecycleAuditMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceReceiptNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptLifecycleActionEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class FinanceReceiptServiceImplTest {

    private static final Long ENTITY_COMPANY_DEPT_ID = 10L;
    private static final String ENTITY_COMPANY_NAME = "示例主体公司";

    private FinanceBankReceiptMapper receiptMapper;
    private FinanceReceiptLifecycleAuditMapper lifecycleAuditMapper;
    private FinanceReceiptNoRedisDAO receiptNoRedisDAO;
    private FinanceEntityCompanyResolver entityCompanyResolver;
    private FinanceReceiptServiceImpl receiptService;

    @BeforeEach
    void setUp() {
        receiptMapper = mock(FinanceBankReceiptMapper.class);
        lifecycleAuditMapper = mock(FinanceReceiptLifecycleAuditMapper.class);
        receiptNoRedisDAO = mock(FinanceReceiptNoRedisDAO.class);
        entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        receiptService = new FinanceReceiptServiceImpl(
                receiptMapper, lifecycleAuditMapper, receiptNoRedisDAO, entityCompanyResolver);
        when(entityCompanyResolver.matchByNameOrError(anyString(), ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    FinanceEntityCompanyResolver.ResolvedCompany[] out = invocation.getArgument(1);
                    out[0] = new FinanceEntityCompanyResolver.ResolvedCompany(
                            ENTITY_COMPANY_DEPT_ID, ENTITY_COMPANY_NAME);
                    return null;
                });
    }

    @Test
    void importReceiptListShouldInsertValidRowsAndReturnFailures() {
        when(receiptNoRedisDAO.generate(LocalDate.now())).thenReturn("RC-20260722-1");
        when(receiptMapper.selectByBankSerialNo("BSN-001")).thenReturn(null);
        FinanceReceiptImportExcelVO valid = row("招商银行 1234", "BSN-001", new BigDecimal("100.50"));
        FinanceReceiptImportExcelVO invalidAmount = row("招商银行 1234", "BSN-002", BigDecimal.ZERO);

        FinanceReceiptImportRespVO respVO = receiptService.importReceiptList(List.of(valid, invalidAmount), 100L);

        assertEquals(List.of("RC-20260722-1"), respVO.getReceiptNos());
        assertEquals("交易金额必须大于 0", respVO.getFailureRows().get(3));
        verify(receiptMapper).insert(argThat((FinanceReceiptDO receipt) ->
                "RC-20260722-1".equals(receipt.getReceiptNo())
                        && ENTITY_COMPANY_DEPT_ID.equals(receipt.getEntityCompanyDeptId())
                        && ENTITY_COMPANY_NAME.equals(receipt.getEntityCompanyName())
                        && receipt.getClaimStatus().equals(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus())
                        && BigDecimal.ZERO.compareTo(receipt.getClaimedAmount()) == 0
                        && new BigDecimal("100.50").compareTo(receipt.getUnclaimedAmount()) == 0));
    }

    @Test
    void importReceiptListShouldRejectEmptyRows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> receiptService.importReceiptList(List.of(), 100L));

        assertEquals("导入银行到款数据不能为空", ex.getMessage());
        verify(receiptMapper, never()).insert(any(FinanceReceiptDO.class));
    }

    @Test
    void importReceiptListShouldRejectDuplicateBankSerialNoInFileAndDatabase() {
        when(receiptMapper.selectByBankSerialNo("BSN-001")).thenReturn(null);
        when(receiptMapper.selectByBankSerialNo("BSN-002")).thenReturn(FinanceReceiptDO.builder().id(1L).build());
        FinanceReceiptImportExcelVO first = row("招商银行 1234", "BSN-001", BigDecimal.ONE);
        FinanceReceiptImportExcelVO duplicatedInFile = row("招商银行 1234", "BSN-001", BigDecimal.TEN);
        FinanceReceiptImportExcelVO duplicatedInDatabase = row("招商银行 1234", "BSN-002", BigDecimal.TEN);

        FinanceReceiptImportRespVO respVO = receiptService.importReceiptList(
                List.of(first, duplicatedInFile, duplicatedInDatabase), 100L);

        assertEquals("银行流水号在文件内重复", respVO.getFailureRows().get(3));
        assertEquals("银行流水号已存在", respVO.getFailureRows().get(4));
        verify(receiptMapper, times(1)).insert(any(FinanceReceiptDO.class));
    }

    @Test
    void importReceiptListShouldAcceptDateOnlyTextAndDateTimeText() {
        when(receiptNoRedisDAO.generate(LocalDate.now())).thenReturn("RC-DATE-1", "RC-DATE-2");
        when(receiptMapper.selectByBankSerialNo(any())).thenReturn(null);
        FinanceReceiptImportExcelVO dateOnly = row("招商银行 1234", "BSN-DATE-1", BigDecimal.ONE);
        dateOnly.setTransactionDate("2026-05-25");
        FinanceReceiptImportExcelVO dateTime = row("招商银行 1234", "BSN-DATE-2", BigDecimal.TEN);
        dateTime.setTransactionDate("2026-05-25 10:15:30");

        FinanceReceiptImportRespVO respVO = receiptService.importReceiptList(List.of(dateOnly, dateTime), 100L);

        assertEquals(List.of("RC-DATE-1", "RC-DATE-2"), respVO.getReceiptNos());
        assertTrue(respVO.getFailureRows().isEmpty());
        verify(receiptMapper).insert(argThat((FinanceReceiptDO r) ->
                LocalDateTime.of(2026, 5, 25, 0, 0, 0).equals(r.getTransactionDate())
                        && "BSN-DATE-1".equals(r.getBankSerialNo())));
        verify(receiptMapper).insert(argThat((FinanceReceiptDO r) ->
                LocalDateTime.of(2026, 5, 25, 10, 15, 30).equals(r.getTransactionDate())
                        && "BSN-DATE-2".equals(r.getBankSerialNo())));
    }

    @Test
    void importReceiptListShouldReturnRowFailureForInvalidDateInsteadOfThrowing() {
        FinanceReceiptImportExcelVO invalidDate = row("招商银行 1234", "BSN-BAD-DATE", BigDecimal.ONE);
        invalidDate.setTransactionDate("not-a-date");
        FinanceReceiptImportExcelVO blankDate = row("招商银行 1234", "BSN-BLANK-DATE", BigDecimal.ONE);
        blankDate.setTransactionDate("  ");

        FinanceReceiptImportRespVO respVO = receiptService.importReceiptList(
                List.of(invalidDate, blankDate), 100L);

        assertTrue(respVO.getReceiptNos().isEmpty());
        assertEquals("交易日期格式错误", respVO.getFailureRows().get(2));
        assertEquals("交易日期不能为空", respVO.getFailureRows().get(3));
        verify(receiptMapper, never()).insert(any(FinanceReceiptDO.class));
    }

    @Test
    void getUnclaimedReceiptPageShouldDelegateToMapper() {
        FinanceReceiptPageReqVO reqVO = new FinanceReceiptPageReqVO();
        PageResult<FinanceReceiptDO> expected = new PageResult<>(List.of(FinanceReceiptDO.builder().id(1L).build()), 1L);
        when(receiptMapper.selectUnclaimedPage(reqVO)).thenReturn(expected);

        assertSame(expected, receiptService.getUnclaimedReceiptPage(reqVO));
    }

    @Test
    void closeReceiptShouldTransitionReceiptWithRemainingAmountAndAppendAudit() {
        when(receiptMapper.selectById(1L)).thenReturn(receipt(1L,
                FinanceReceiptClaimStatusEnum.PARTIALLY_CLAIMED.getStatus(), "40.00", "60.00"));
        when(receiptMapper.closeIfStatus(1L,
                FinanceReceiptClaimStatusEnum.PARTIALLY_CLAIMED.getStatus())).thenReturn(1);
        when(lifecycleAuditMapper.insert(any(FinanceReceiptLifecycleAuditDO.class))).thenReturn(1);

        receiptService.closeReceipt(1L, 900L, "测试操作人", "尾款不再收取");

        verify(receiptMapper).closeIfStatus(1L,
                FinanceReceiptClaimStatusEnum.PARTIALLY_CLAIMED.getStatus());
        verify(lifecycleAuditMapper).insert(argThat(audit -> audit.getReceiptId().equals(1L)
                && audit.getOperatorId().equals(900L)
                && "测试操作人".equals(audit.getOperatorName())
                && audit.getAction().equals(FinanceReceiptLifecycleActionEnum.CLOSE.getAction())
                && "尾款不再收取".equals(audit.getReason())
                && audit.getActionTime() != null));
    }

    @Test
    void closeReceiptShouldRejectBlankReasonWithoutReadingReceipt() {
        assertThrows(RuntimeException.class, () -> receiptService.closeReceipt(1L, 900L, "测试操作人", "  "));

        verifyNoInteractions(receiptMapper, lifecycleAuditMapper);
    }

    @Test
    void closeReceiptShouldRejectReceiptWithoutRemainingAmount() {
        when(receiptMapper.selectById(1L)).thenReturn(receipt(1L,
                FinanceReceiptClaimStatusEnum.FULLY_CLAIMED.getStatus(), "100.00", "0.00"));

        assertThrows(RuntimeException.class, () -> receiptService.closeReceipt(1L, 900L, "测试操作人", "已全部认领"));

        verify(receiptMapper, never()).closeIfStatus(anyLong(), anyInt());
        verifyNoInteractions(lifecycleAuditMapper);
    }

    @Test
    void closeReceiptShouldRejectConcurrentStatusChangeWithoutAudit() {
        when(receiptMapper.selectById(1L)).thenReturn(receipt(1L,
                FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus(), "0.00", "100.00"));
        when(receiptMapper.closeIfStatus(1L,
                FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus())).thenReturn(0);

        assertThrows(RuntimeException.class, () -> receiptService.closeReceipt(1L, 900L, "测试操作人", "不再认领"));

        verifyNoInteractions(lifecycleAuditMapper);
    }

    @Test
    void reopenReceiptShouldTransitionClosedReceiptAndAppendAudit() {
        when(receiptMapper.selectById(1L)).thenReturn(receipt(1L,
                FinanceReceiptClaimStatusEnum.CLOSED.getStatus(), "40.00", "60.00"));
        when(receiptMapper.reopenIfClosed(1L)).thenReturn(1);
        when(lifecycleAuditMapper.insert(any(FinanceReceiptLifecycleAuditDO.class))).thenReturn(1);

        receiptService.reopenReceipt(1L, 901L, "测试操作人", "客户恢复付款");

        verify(receiptMapper).reopenIfClosed(1L);
        verify(lifecycleAuditMapper).insert(argThat(audit -> audit.getReceiptId().equals(1L)
                && audit.getOperatorId().equals(901L)
                && "测试操作人".equals(audit.getOperatorName())
                && audit.getAction().equals(FinanceReceiptLifecycleActionEnum.REOPEN.getAction())
                && "客户恢复付款".equals(audit.getReason())
                && audit.getActionTime() != null));
    }

    @Test
    void reopenReceiptShouldOnlyAllowClosedReceipt() {
        when(receiptMapper.selectById(1L)).thenReturn(receipt(1L,
                FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus(), "0.00", "100.00"));

        assertThrows(RuntimeException.class, () -> receiptService.reopenReceipt(1L, 901L, "测试操作人", "误关闭"));

        verify(receiptMapper, never()).reopenIfClosed(anyLong());
        verifyNoInteractions(lifecycleAuditMapper);
    }

    @Test
    void reopenReceiptShouldRejectConcurrentStatusChangeWithoutAudit() {
        when(receiptMapper.selectById(1L)).thenReturn(receipt(1L,
                FinanceReceiptClaimStatusEnum.CLOSED.getStatus(), "0.00", "100.00"));
        when(receiptMapper.reopenIfClosed(1L)).thenReturn(0);

        assertThrows(RuntimeException.class, () -> receiptService.reopenReceipt(1L, 901L, "测试操作人", "误关闭"));

        verifyNoInteractions(lifecycleAuditMapper);
    }

    @Test
    void lifecycleTransitionsShouldBeTransactional() throws NoSuchMethodException {
        assertNotNull(FinanceReceiptServiceImpl.class
                .getMethod("closeReceipt", Long.class, Long.class, String.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        assertNotNull(FinanceReceiptServiceImpl.class
                .getMethod("reopenReceipt", Long.class, Long.class, String.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void getLifecycleAuditListShouldDelegateToImmutableAuditMapper() {
        List<FinanceReceiptLifecycleAuditDO> expected = List.of(
                FinanceReceiptLifecycleAuditDO.builder().id(2L).receiptId(1L)
                        .action(FinanceReceiptLifecycleActionEnum.REOPEN.getAction()).reason("恢复认领").build(),
                FinanceReceiptLifecycleAuditDO.builder().id(1L).receiptId(1L)
                        .action(FinanceReceiptLifecycleActionEnum.CLOSE.getAction()).reason("停止认领").build());
        when(lifecycleAuditMapper.selectListByReceiptId(1L)).thenReturn(expected);

        assertSame(expected, receiptService.getLifecycleAuditList(1L));
        verify(lifecycleAuditMapper).selectListByReceiptId(1L);
    }

    private static FinanceReceiptImportExcelVO row(String bankAccount, String bankSerialNo, BigDecimal amount) {
        return FinanceReceiptImportExcelVO.builder()
                .entityCompanyName(ENTITY_COMPANY_NAME)
                .bankAccount(bankAccount)
                .transactionDate("2026-07-22 10:30:00")
                .payerName("客户 A")
                .payerAccount("6222000000000000000")
                .transactionAmount(amount)
                .summary("合同款")
                .bankSerialNo(bankSerialNo)
                .build();
    }

    private static FinanceReceiptDO receipt(Long id, Integer claimStatus, String claimedAmount,
                                            String unclaimedAmount) {
        return FinanceReceiptDO.builder().id(id).claimStatus(claimStatus)
                .claimedAmount(new BigDecimal(claimedAmount))
                .unclaimedAmount(new BigDecimal(unclaimedAmount)).build();
    }

}
