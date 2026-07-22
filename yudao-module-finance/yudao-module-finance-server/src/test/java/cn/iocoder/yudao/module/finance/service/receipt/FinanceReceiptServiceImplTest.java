package cn.iocoder.yudao.module.finance.service.receipt;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptImportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceBankReceiptMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceReceiptNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class FinanceReceiptServiceImplTest {

    private FinanceBankReceiptMapper receiptMapper;
    private FinanceReceiptNoRedisDAO receiptNoRedisDAO;
    private FinanceReceiptServiceImpl receiptService;

    @BeforeEach
    void setUp() {
        receiptMapper = mock(FinanceBankReceiptMapper.class);
        receiptNoRedisDAO = mock(FinanceReceiptNoRedisDAO.class);
        receiptService = new FinanceReceiptServiceImpl(receiptMapper, receiptNoRedisDAO);
    }

    @Test
    void importReceiptListShouldInsertValidRowsAndReturnFailures() {
        when(receiptNoRedisDAO.generate(LocalDate.of(2026, 7, 22))).thenReturn("RC-20260722-1");
        when(receiptMapper.selectByBankSerialNo("BSN-001")).thenReturn(null);
        FinanceReceiptImportExcelVO valid = row("招商银行 1234", "BSN-001", new BigDecimal("100.50"));
        FinanceReceiptImportExcelVO invalidAmount = row("招商银行 1234", "BSN-002", BigDecimal.ZERO);

        FinanceReceiptImportRespVO respVO = receiptService.importReceiptList(List.of(valid, invalidAmount), 100L);

        assertEquals(List.of("RC-20260722-1"), respVO.getReceiptNos());
        assertEquals("交易金额必须大于 0", respVO.getFailureRows().get(3));
        verify(receiptMapper).insert(argThat((FinanceReceiptDO receipt) ->
                "RC-20260722-1".equals(receipt.getReceiptNo())
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
    void getUnclaimedReceiptPageShouldDelegateToMapper() {
        FinanceReceiptPageReqVO reqVO = new FinanceReceiptPageReqVO();
        PageResult<FinanceReceiptDO> expected = new PageResult<>(List.of(FinanceReceiptDO.builder().id(1L).build()), 1L);
        when(receiptMapper.selectUnclaimedPage(reqVO)).thenReturn(expected);

        assertSame(expected, receiptService.getUnclaimedReceiptPage(reqVO));
    }

    private static FinanceReceiptImportExcelVO row(String bankAccount, String bankSerialNo, BigDecimal amount) {
        return FinanceReceiptImportExcelVO.builder()
                .bankAccount(bankAccount)
                .transactionDate(LocalDateTime.of(2026, 7, 22, 10, 30))
                .payerName("客户 A")
                .payerAccount("6222000000000000000")
                .transactionAmount(amount)
                .summary("合同款")
                .bankSerialNo(bankSerialNo)
                .build();
    }

}
