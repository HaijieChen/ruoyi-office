package cn.iocoder.yudao.module.finance.service.companyaccount;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.mysql.companyaccount.FinanceCompanyBankAccountMapper;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceCompanyBankAccountServiceImplTest {

    private FinanceCompanyBankAccountMapper mapper;
    private FinanceEntityCompanyResolver entityCompanyResolver;
    private FinanceCompanyBankAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceCompanyBankAccountMapper.class);
        entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        service = new FinanceCompanyBankAccountServiceImpl(mapper, entityCompanyResolver);
        when(entityCompanyResolver.requireByDeptId(20L))
                .thenReturn(new FinanceEntityCompanyResolver.ResolvedCompany(20L, "主体甲", "CNY"));
        doAnswer(inv -> {
            FinanceCompanyBankAccountDO row = inv.getArgument(0);
            row.setId(1L);
            return 1;
        }).when(mapper).insert(any(FinanceCompanyBankAccountDO.class));
    }

    private FinanceCompanyBankAccountSaveReqVO baseReq() {
        FinanceCompanyBankAccountSaveReqVO req = new FinanceCompanyBankAccountSaveReqVO();
        req.setEntityCompanyDeptId(20L);
        req.setAccountName("基本户");
        req.setBankName("工行");
        req.setAccountHolder("主体甲");
        req.setAccountNo("62220001");
        req.setCurrency("CNY");
        return req;
    }

    @Test
    void createOk() {
        Long id = service.create(baseReq());
        assertEquals(1L, id);
        verify(mapper).insert(any(FinanceCompanyBankAccountDO.class));
        // 仅通过 entityCompanyDeptId 关联组织，不写公司名称字段
        verify(entityCompanyResolver).requireByDeptId(20L);
    }

    @Test
    void createRejectsInvalidCompany() {
        when(entityCompanyResolver.requireByDeptId(99L))
                .thenThrow(new ServiceException(ENTITY_COMPANY_INVALID));
        FinanceCompanyBankAccountSaveReqVO req = baseReq();
        req.setEntityCompanyDeptId(99L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(req));
        assertEquals(ENTITY_COMPANY_INVALID.getCode(), ex.getCode());
    }

    @Test
    void createRejectsDuplicateAccountNo() {
        when(mapper.selectByCompanyAndAccountNo(20L, "62220001"))
                .thenReturn(FinanceCompanyBankAccountDO.builder().id(9L).build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.create(baseReq()));
        assertEquals(COMPANY_BANK_ACCOUNT_NO_DUPLICATE.getCode(), ex.getCode());
    }

    @Test
    void requireEnabledRejectsDisabled() {
        when(mapper.selectById(3L)).thenReturn(FinanceCompanyBankAccountDO.builder()
                .id(3L)
                .entityCompanyDeptId(20L)
                .status(FinanceCompanyBankAccountDO.STATUS_DISABLE)
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.requireEnabledForEntityCompany(3L, 20L));
        assertEquals(COMPANY_BANK_ACCOUNT_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void requireEnabledRejectsEntityMismatch() {
        when(mapper.selectById(3L)).thenReturn(FinanceCompanyBankAccountDO.builder()
                .id(3L)
                .entityCompanyDeptId(20L)
                .status(FinanceCompanyBankAccountDO.STATUS_ENABLE)
                .build());
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.requireEnabledForEntityCompany(3L, 21L));
        assertEquals(COMPANY_BANK_ACCOUNT_ENTITY_MISMATCH.getCode(), ex.getCode());
    }

}
