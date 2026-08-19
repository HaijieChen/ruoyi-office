package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceBankBalanceReportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.opening.FinanceBankOpeningBalanceDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.dal.mysql.companyaccount.FinanceCompanyBankAccountMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.opening.FinanceBankOpeningBalanceMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.receipt.FinanceBankReceiptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class FinanceBankBalanceReportServiceImplTest {

    private FinanceBankBalanceReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FinanceBankBalanceReportServiceImpl(
                mock(FinanceCompanyBankAccountMapper.class),
                mock(FinanceBankOpeningBalanceMapper.class),
                mock(FinanceBankReceiptMapper.class),
                mock(FinancePaymentPayLineMapper.class));
    }

    @Test
    void openingPlusIncomeMinusPay() {
        FinanceCompanyBankAccountDO account = account(1L, 20L, "基本户", "62220001");
        FinanceBankOpeningBalanceDO opening = FinanceBankOpeningBalanceDO.builder()
                .accountId(1L)
                .asOfDate(LocalDate.of(2026, 7, 31))
                .amount(new BigDecimal("100.00"))
                .currency("CNY")
                .build();
        FinanceReceiptDO receipt = FinanceReceiptDO.builder()
                .entityCompanyDeptId(20L)
                .bankAccount("62220001")
                .transactionDate(LocalDateTime.of(2026, 8, 10, 10, 0))
                .transactionAmount(new BigDecimal("50.00"))
                .currency("CNY")
                .build();
        FinancePaymentPayLineDO pay = FinancePaymentPayLineDO.builder()
                .companyBankAccountId(1L)
                .actualPayDate(LocalDate.of(2026, 8, 12))
                .payAmount(new BigDecimal("30.00"))
                .currencySnapshot("CNY")
                .build();
        FinanceBankBalanceReportRespVO resp = service.aggregate(
                List.of(account), List.of(opening), List.of(receipt), List.of(pay),
                LocalDate.of(2026, 8, 31));
        assertEquals(1, resp.getList().size());
        assertEquals(new BigDecimal("120.00"), resp.getList().get(0).getBalanceAmount());
        assertEquals(0L, resp.getExcludedFxCount());
        assertEquals(0L, resp.getUnmatchedReceiptCount());
        assertEquals(BigDecimal.ZERO, resp.getList().get(0).getReimbursementExpenseAmount());
    }

    @Test
    void usdReceiptExcluded() {
        FinanceCompanyBankAccountDO account = account(1L, 20L, "基本户", "62220001");
        FinanceReceiptDO receipt = FinanceReceiptDO.builder()
                .entityCompanyDeptId(20L)
                .bankAccount("62220001")
                .transactionDate(LocalDateTime.of(2026, 8, 10, 10, 0))
                .transactionAmount(new BigDecimal("50.00"))
                .currency("USD")
                .build();
        FinanceBankBalanceReportRespVO resp = service.aggregate(
                List.of(account), List.of(), List.of(receipt), List.of(),
                LocalDate.of(2026, 8, 31));
        assertEquals(BigDecimal.ZERO, resp.getList().get(0).getIncomeAmount());
        assertEquals(1L, resp.getExcludedFxCount());
    }

    @Test
    void nameOnlyReceiptMatchesAccountName() {
        FinanceCompanyBankAccountDO account = account(1L, 20L, "工行基本户", "62220001");
        FinanceReceiptDO receipt = FinanceReceiptDO.builder()
                .entityCompanyDeptId(20L)
                .bankAccount("工行基本户")
                .transactionDate(LocalDateTime.of(2026, 8, 10, 10, 0))
                .transactionAmount(new BigDecimal("50.00"))
                .currency("CNY")
                .build();
        FinanceBankBalanceReportRespVO resp = service.aggregate(
                List.of(account), List.of(), List.of(receipt), List.of(),
                LocalDate.of(2026, 8, 31));
        assertEquals(new BigDecimal("50.00"), resp.getList().get(0).getIncomeAmount());
    }

    private static FinanceCompanyBankAccountDO account(Long id, Long deptId, String name, String no) {
        return FinanceCompanyBankAccountDO.builder()
                .id(id)
                .entityCompanyDeptId(deptId)
                .accountName(name)
                .accountNo(no)
                .currency("CNY")
                .build();
    }
}
