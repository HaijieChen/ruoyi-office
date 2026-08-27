package cn.iocoder.yudao.module.finance.service.feepayment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentImportRespVO;
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
import java.util.List;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                        .accountHolder("甲公司")
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
        assertEquals("甲公司", row.getAccountName());
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
    void updateRejectsAccountFromOtherCompany() {
        when(mapper.selectById(1L)).thenReturn(FinanceHandlingFeePaymentDO.builder()
                .id(1L).entityCompanyDeptId(20L).companyBankAccountId(8L).build());
        when(bankAccountService.requireEnabledForEntityCompany(99L, 20L))
                .thenThrow(new ServiceException(COMPANY_BANK_ACCOUNT_ENTITY_MISMATCH));
        FinanceHandlingFeePaymentSaveReqVO req = baseReq();
        req.setId(1L);
        req.setCompanyBankAccountId(99L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.update(req));
        assertEquals(COMPANY_BANK_ACCOUNT_ENTITY_MISMATCH.getCode(), ex.getCode());
        verify(mapper, never()).updateById(any(FinanceHandlingFeePaymentDO.class));
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

    @Test
    void importEmptyThrows() {
        ServiceException ex = assertThrows(ServiceException.class, () -> service.importExcel(List.of()));
        assertEquals(HANDLING_FEE_PAYMENT_IMPORT_EMPTY.getCode(), ex.getCode());
    }

    @Test
    void importOkDefaultsCurrencyFromAccount() {
        stubCompanyMatch("主体甲", 20L);
        when(bankAccountService.listEnabledByEntityCompany(20L)).thenReturn(List.of(enabledCnyAccount()));

        FinanceHandlingFeePaymentImportExcelVO row = FinanceHandlingFeePaymentImportExcelVO.builder()
                .feeDate(LocalDate.of(2026, 8, 1))
                .amount(new BigDecimal("5"))
                .currency("")
                .entityCompanyName("主体甲")
                .accountNo("62220001")
                .build();

        FinanceHandlingFeePaymentImportRespVO resp = service.importExcel(List.of(row));
        assertEquals(1, resp.getCreatedIds().size());
        ArgumentCaptor<FinanceHandlingFeePaymentDO> captor =
                ArgumentCaptor.forClass(FinanceHandlingFeePaymentDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("CNY", captor.getValue().getCurrency());
    }

    @Test
    void importFailsUnknownCompany() {
        when(entityCompanyResolver.loadEnabledCompanies()).thenReturn(List.of());
        when(entityCompanyResolver.matchByNameOrError(eq("未知公司"), any(), any()))
                .thenReturn("主体公司不存在或未启用");

        FinanceHandlingFeePaymentImportExcelVO row = FinanceHandlingFeePaymentImportExcelVO.builder()
                .feeDate(LocalDate.of(2026, 8, 1))
                .amount(new BigDecimal("5"))
                .entityCompanyName("未知公司")
                .accountNo("62220001")
                .build();

        FinanceHandlingFeePaymentImportRespVO resp = service.importExcel(List.of(row));
        assertEquals("主体公司不存在或未启用", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceHandlingFeePaymentDO.class));
    }

    @Test
    void importFailsUnknownAccount() {
        stubCompanyMatch("主体甲", 20L);
        when(bankAccountService.listEnabledByEntityCompany(20L)).thenReturn(List.of(enabledCnyAccount()));

        FinanceHandlingFeePaymentImportExcelVO row = FinanceHandlingFeePaymentImportExcelVO.builder()
                .feeDate(LocalDate.of(2026, 8, 1))
                .amount(new BigDecimal("5"))
                .entityCompanyName("主体甲")
                .accountNo("99999999")
                .build();

        FinanceHandlingFeePaymentImportRespVO resp = service.importExcel(List.of(row));
        assertTrue(resp.getFailureRows().get(2).contains("银行账号"));
        verify(mapper, never()).insert(any(FinanceHandlingFeePaymentDO.class));
    }

    @Test
    void importPartial() {
        stubCompanyMatch("主体甲", 20L);
        when(entityCompanyResolver.matchByNameOrError(eq("未知公司"), any(), any()))
                .thenReturn("主体公司不存在或未启用");
        when(bankAccountService.listEnabledByEntityCompany(20L)).thenReturn(List.of(enabledCnyAccount()));

        FinanceHandlingFeePaymentImportExcelVO ok = FinanceHandlingFeePaymentImportExcelVO.builder()
                .feeDate(LocalDate.of(2026, 8, 1))
                .amount(new BigDecimal("5"))
                .entityCompanyName("主体甲")
                .accountNo("62220001")
                .build();
        FinanceHandlingFeePaymentImportExcelVO fail = FinanceHandlingFeePaymentImportExcelVO.builder()
                .feeDate(LocalDate.of(2026, 8, 2))
                .amount(new BigDecimal("6"))
                .entityCompanyName("未知公司")
                .accountNo("62220001")
                .build();

        FinanceHandlingFeePaymentImportRespVO resp = service.importExcel(List.of(ok, fail));
        assertEquals(1, resp.getCreatedIds().size());
        assertEquals("主体公司不存在或未启用", resp.getFailureRows().get(3));
        verify(mapper, times(1)).insert(any(FinanceHandlingFeePaymentDO.class));
    }

    private void stubCompanyMatch(String name, Long deptId) {
        when(entityCompanyResolver.loadEnabledCompanies()).thenReturn(List.of());
        doAnswer(inv -> {
            FinanceEntityCompanyResolver.ResolvedCompany[] out = inv.getArgument(1);
            out[0] = new FinanceEntityCompanyResolver.ResolvedCompany(deptId, name, "CNY");
            return null;
        }).when(entityCompanyResolver).matchByNameOrError(eq(name), any(), any());
    }

    private FinanceCompanyBankAccountDO enabledCnyAccount() {
        return FinanceCompanyBankAccountDO.builder()
                .id(8L).entityCompanyDeptId(20L).accountName("基本户")
                .accountHolder("甲公司")
                .bankName("工行").accountNo("62220001")
                .currency("CNY").status(0).build();
    }

}
