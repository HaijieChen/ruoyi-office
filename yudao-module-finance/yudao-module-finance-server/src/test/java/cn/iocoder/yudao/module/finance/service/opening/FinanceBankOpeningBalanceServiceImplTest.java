package cn.iocoder.yudao.module.finance.service.opening;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalanceSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.opening.FinanceBankOpeningBalanceDO;
import cn.iocoder.yudao.module.finance.dal.mysql.opening.FinanceBankOpeningBalanceMapper;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.COMPANY_BANK_ACCOUNT_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanceBankOpeningBalanceServiceImplTest {

    private FinanceBankOpeningBalanceMapper mapper;
    private FinanceCompanyBankAccountService bankAccountService;
    private FinanceBankOpeningBalanceServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceBankOpeningBalanceMapper.class);
        bankAccountService = mock(FinanceCompanyBankAccountService.class);
        service = new FinanceBankOpeningBalanceServiceImpl(mapper, bankAccountService);
        when(bankAccountService.get(10L)).thenReturn(FinanceCompanyBankAccountDO.builder()
                .id(10L)
                .currency("CNY")
                .status(FinanceCompanyBankAccountDO.STATUS_ENABLE)
                .build());
        when(bankAccountService.get(20L)).thenReturn(FinanceCompanyBankAccountDO.builder()
                .id(20L)
                .currency("CNY")
                .status(FinanceCompanyBankAccountDO.STATUS_ENABLE)
                .build());
        doAnswer(inv -> {
            FinanceBankOpeningBalanceDO row = inv.getArgument(0);
            if (row.getId() == null) {
                row.setId(row.getAccountId() == 20L ? 2L : 1L);
            }
            return 1;
        }).when(mapper).insert(any(FinanceBankOpeningBalanceDO.class));
    }

    private FinanceBankOpeningBalanceSaveReqVO req(Long accountId, String amount) {
        FinanceBankOpeningBalanceSaveReqVO vo = new FinanceBankOpeningBalanceSaveReqVO();
        vo.setAccountId(accountId);
        vo.setAsOfDate(LocalDate.of(2026, 8, 31));
        vo.setAmount(new BigDecimal(amount));
        vo.setCurrency("CNY");
        return vo;
    }

    @Test
    void upsertCreatesOneOpeningPerAccount() {
        when(mapper.selectByAccountId(10L)).thenReturn(null);
        when(mapper.selectByAccountId(20L)).thenReturn(null);

        Long first = service.upsert(req(10L, "100.00"));
        Long second = service.upsert(req(20L, "200.00"));

        assertEquals(1L, first);
        assertEquals(2L, second);
        verify(mapper, times(2)).insert(any(FinanceBankOpeningBalanceDO.class));
        verify(mapper, never()).updateById(any(FinanceBankOpeningBalanceDO.class));
    }

    @Test
    void secondWriteOnSameAccountUpsertsExistingRow() {
        FinanceBankOpeningBalanceDO existing = FinanceBankOpeningBalanceDO.builder()
                .id(7L)
                .accountId(10L)
                .asOfDate(LocalDate.of(2026, 7, 31))
                .amount(new BigDecimal("50.00"))
                .currency("CNY")
                .build();
        when(mapper.selectByAccountId(10L)).thenReturn(existing);

        Long id = service.upsert(req(10L, "180.00"));

        assertEquals(7L, id);
        verify(mapper, never()).insert(any(FinanceBankOpeningBalanceDO.class));
        ArgumentCaptor<FinanceBankOpeningBalanceDO> captor =
                ArgumentCaptor.forClass(FinanceBankOpeningBalanceDO.class);
        verify(mapper).updateById(captor.capture());
        assertEquals(7L, captor.getValue().getId());
        assertEquals(10L, captor.getValue().getAccountId());
        assertEquals(LocalDate.of(2026, 8, 31), captor.getValue().getAsOfDate());
        assertEquals(0, new BigDecimal("180.00").compareTo(captor.getValue().getAmount()));
        assertEquals("CNY", captor.getValue().getCurrency());
    }

    @Test
    void queryByAccountIdDoesNotUpsert() {
        when(mapper.selectByAccountId(10L)).thenReturn(null);

        assertNull(service.getByAccountId(10L));

        verify(mapper).selectByAccountId(10L);
        verify(mapper, never()).insert(any(FinanceBankOpeningBalanceDO.class));
        verify(mapper, never()).updateById(any(FinanceBankOpeningBalanceDO.class));
    }

    @Test
    void upsertRejectsMissingAccount() {
        when(bankAccountService.get(99L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.upsert(req(99L, "1.00")));
        assertEquals(COMPANY_BANK_ACCOUNT_NOT_EXISTS.getCode(), ex.getCode());
        verify(mapper, never()).insert(any(FinanceBankOpeningBalanceDO.class));
        verify(mapper, never()).updateById(any(FinanceBankOpeningBalanceDO.class));
    }

}
