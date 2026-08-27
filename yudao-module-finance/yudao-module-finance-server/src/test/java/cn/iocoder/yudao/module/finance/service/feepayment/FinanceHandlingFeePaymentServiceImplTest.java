package cn.iocoder.yudao.module.finance.service.feepayment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.feepayment.FinanceHandlingFeePaymentDO;
import cn.iocoder.yudao.module.finance.dal.mysql.feepayment.FinanceHandlingFeePaymentMapper;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.companyaccount.FinanceCompanyBankAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanceHandlingFeePaymentServiceImplTest {

    private FinanceHandlingFeePaymentMapper mapper;
    private FinanceEntityCompanyResolver entityCompanyResolver;
    private FinanceCompanyBankAccountService bankAccountService;
    private FinanceHandlingFeePaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceHandlingFeePaymentMapper.class);
        entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        bankAccountService = mock(FinanceCompanyBankAccountService.class);
        service = new FinanceHandlingFeePaymentServiceImpl(mapper, entityCompanyResolver, bankAccountService);
        when(entityCompanyResolver.requireByDeptId(20L))
                .thenReturn(new FinanceEntityCompanyResolver.ResolvedCompany(20L, "主体甲", "CNY"));
        when(bankAccountService.requireEnabledForEntityCompany(8L, 20L))
                .thenReturn(FinanceCompanyBankAccountDO.builder()
                        .id(8L).entityCompanyDeptId(20L).accountName("基本户")
                        .bankName("工行").accountNo("62220001")
                        .currency("CNY").status(0).build());
        doAnswer(inv -> {
            FinanceHandlingFeePaymentDO row = inv.getArgument(0);
            row.setId(1L);
            return 1;
        }).when(mapper).insert(any(FinanceHandlingFeePaymentDO.class));
    }

    private FinanceHandlingFeePaymentSaveReqVO baseReq() {
        FinanceHandlingFeePaymentSaveReqVO req = new FinanceHandlingFeePaymentSaveReqVO();
        req.setFeeDate(LocalDate.of(2026, 8, 1));
        req.setAmount(new BigDecimal("10.00"));
        req.setCurrency("CNY");
        req.setEntityCompanyDeptId(20L);
        req.setCompanyBankAccountId(8L);
        return req;
    }

    @Test
    void createOk() {
        Long id = service.create(baseReq());
        assertEquals(1L, id);
        ArgumentCaptor<FinanceHandlingFeePaymentDO> captor =
                ArgumentCaptor.forClass(FinanceHandlingFeePaymentDO.class);
        verify(mapper).insert(captor.capture());
        FinanceHandlingFeePaymentDO row = captor.getValue();
        assertEquals("主体甲", row.getEntityCompanyName());
        assertEquals("****0001", row.getAccountNoMasked());
    }

    @Test
    void createRejectsInvalidCompany() {
        when(entityCompanyResolver.requireByDeptId(99L))
                .thenThrow(new ServiceException(ENTITY_COMPANY_INVALID));
        FinanceHandlingFeePaymentSaveReqVO req = baseReq();
        req.setEntityCompanyDeptId(99L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req));
        assertEquals(ENTITY_COMPANY_INVALID.getCode(), ex.getCode());
    }

    @Test
    void createRejectsDisabledAccount() {
        when(bankAccountService.requireEnabledForEntityCompany(8L, 20L))
                .thenThrow(new ServiceException(COMPANY_BANK_ACCOUNT_DISABLED));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(baseReq()));
        assertEquals(COMPANY_BANK_ACCOUNT_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void createRejectsNonPositiveAmount() {
        FinanceHandlingFeePaymentSaveReqVO req = baseReq();
        req.setAmount(BigDecimal.ZERO);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req));
        assertEquals(HANDLING_FEE_PAYMENT_AMOUNT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void updateMissingThrowsNotExists() {
        when(mapper.selectById(1L)).thenReturn(null);
        FinanceHandlingFeePaymentSaveReqVO req = baseReq();
        req.setId(1L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.update(req));
        assertEquals(HANDLING_FEE_PAYMENT_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void deleteOk() {
        when(mapper.selectById(1L)).thenReturn(FinanceHandlingFeePaymentDO.builder().id(1L).build());
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

}
