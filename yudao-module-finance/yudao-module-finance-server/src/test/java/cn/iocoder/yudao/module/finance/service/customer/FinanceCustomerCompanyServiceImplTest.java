package cn.iocoder.yudao.module.finance.service.customer;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanySaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.customer.FinanceCustomerCompanyMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceCustomerCompanyNoRedisDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class FinanceCustomerCompanyServiceImplTest {

    private FinanceCustomerCompanyMapper mapper;
    private FinanceCustomerCompanyNoRedisDAO noRedisDAO;
    private FinanceCustomerCompanyServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceCustomerCompanyMapper.class);
        noRedisDAO = mock(FinanceCustomerCompanyNoRedisDAO.class);
        service = new FinanceCustomerCompanyServiceImpl(mapper, noRedisDAO);
        when(noRedisDAO.generate(any(LocalDate.class))).thenReturn("CC-20260731-1");
        doAnswer(inv -> {
            FinanceCustomerCompanyDO c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        }).when(mapper).insert(any(FinanceCustomerCompanyDO.class));
    }

    @Test
    void createWithNameAndTaxOnlyShouldSucceedAsCustomerOnly() {
        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setName("客户甲");
        req.setTaxNo("91110000TEST0001");
        Long id = service.createCustomerCompany(req);
        assertEquals(1L, id);

        ArgumentCaptor<FinanceCustomerCompanyDO> cap = ArgumentCaptor.forClass(FinanceCustomerCompanyDO.class);
        verify(mapper).insert(cap.capture());
        assertTrue(cap.getValue().getIsCustomer());
        assertFalse(cap.getValue().getIsSupplier());
    }

    @Test
    void createSupplierWithoutBankShouldFail() {
        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setName("供应商甲");
        req.setTaxNo("91110000SUP001");
        req.setIsCustomer(false);
        req.setIsSupplier(true);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createCustomerCompany(req));
        assertEquals(CUSTOMER_COMPANY_SUPPLIER_BANK_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void createSupplierWithBankShouldSucceed() {
        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setName("供应商甲");
        req.setTaxNo("91110000SUP001");
        req.setIsCustomer(false);
        req.setIsSupplier(true);
        req.setBankName("开户行");
        req.setBankAccount("62220001");

        Long id = service.createCustomerCompany(req);
        assertEquals(1L, id);
        ArgumentCaptor<FinanceCustomerCompanyDO> cap = ArgumentCaptor.forClass(FinanceCustomerCompanyDO.class);
        verify(mapper).insert(cap.capture());
        assertFalse(cap.getValue().getIsCustomer());
        assertTrue(cap.getValue().getIsSupplier());
    }

    @Test
    void createNoRoleShouldFail() {
        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setName("空角色");
        req.setTaxNo("91110000NONE");
        req.setIsCustomer(false);
        req.setIsSupplier(false);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createCustomerCompany(req));
        assertEquals(CUSTOMER_COMPANY_ROLE_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void createDuplicateTaxNoShouldFail() {
        when(mapper.selectByTaxNo("91110000TEST0001")).thenReturn(
                FinanceCustomerCompanyDO.builder().id(9L).taxNo("91110000TEST0001").build());
        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setName("客户乙");
        req.setTaxNo("91110000TEST0001");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createCustomerCompany(req));
        assertEquals(CUSTOMER_COMPANY_TAX_NO_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void createShouldMapDuplicateKeyExceptionToTaxNoExists() {
        when(mapper.selectByTaxNo(any())).thenReturn(null);
        doThrow(new DuplicateKeyException(
                "Duplicate entry '91110000RACE' for key 'uk_tax_no_tenant_deleted'"))
                .when(mapper).insert(any(FinanceCustomerCompanyDO.class));

        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setName("竞态客户");
        req.setTaxNo("91110000RACE");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createCustomerCompany(req));
        assertEquals(CUSTOMER_COMPANY_TAX_NO_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void updateShouldMapDuplicateKeyExceptionToTaxNoExists() {
        when(mapper.selectById(1L)).thenReturn(FinanceCustomerCompanyDO.builder()
                .id(1L).code("CC-1").name("客户甲").taxNo("OLD").status(0)
                .isCustomer(true).isSupplier(false).build());
        when(mapper.selectByTaxNo("NEWTAX")).thenReturn(null);
        doThrow(new DuplicateKeyException("Duplicate entry for key 'uk_tax_no_tenant_deleted'"))
                .when(mapper).update(isNull(), any());

        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setId(1L);
        req.setName("客户甲");
        req.setTaxNo("NEWTAX");
        req.setIsCustomer(true);
        req.setIsSupplier(false);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateCustomerCompany(req));
        assertEquals(CUSTOMER_COMPANY_TAX_NO_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void getEnabledCustomerShouldRejectDisabled() {
        when(mapper.selectById(2L)).thenReturn(FinanceCustomerCompanyDO.builder()
                .id(2L).status(FinanceCustomerCompanyDO.STATUS_DISABLE).isCustomer(true).build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getEnabledCustomerCompany(2L));
        assertEquals(INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void getEnabledCustomerShouldRejectSupplierOnly() {
        when(mapper.selectById(3L)).thenReturn(FinanceCustomerCompanyDO.builder()
                .id(3L).status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                .isCustomer(false).isSupplier(true)
                .bankName("行").bankAccount("号").build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getEnabledCustomerCompany(3L));
        assertEquals(CUSTOMER_COMPANY_NOT_CUSTOMER_ROLE.getCode(), ex.getCode());
    }

    @Test
    void getEnabledSupplierShouldRejectCustomerOnly() {
        when(mapper.selectById(4L)).thenReturn(FinanceCustomerCompanyDO.builder()
                .id(4L).status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                .isCustomer(true).isSupplier(false).build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getEnabledSupplierCompany(4L));
        assertEquals(CUSTOMER_COMPANY_NOT_SUPPLIER_ROLE.getCode(), ex.getCode());
    }

    @Test
    void getEnabledSupplierShouldRejectMissingBank() {
        when(mapper.selectById(5L)).thenReturn(FinanceCustomerCompanyDO.builder()
                .id(5L).status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                .isCustomer(false).isSupplier(true).build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getEnabledSupplierCompany(5L));
        assertEquals(CUSTOMER_COMPANY_NOT_SUPPLIER_ROLE.getCode(), ex.getCode());
    }

    @Test
    void getEnabledSupplierOk() {
        when(mapper.selectById(6L)).thenReturn(FinanceCustomerCompanyDO.builder()
                .id(6L).status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                .isCustomer(true).isSupplier(true)
                .bankName("行").bankAccount("6222").build());
        FinanceCustomerCompanyDO c = service.getEnabledSupplierCompany(6L);
        assertEquals(6L, c.getId());
    }

    @Test
    void composeRules() {
        assertEquals("A B", FinanceCustomerCompanyService.joinNonEmpty("A", "B"));
        assertEquals("A", FinanceCustomerCompanyService.joinNonEmpty("A", null, "  "));
        assertNull(FinanceCustomerCompanyService.joinNonEmpty(null, "  "));
        FinanceCustomerCompanyDO c = FinanceCustomerCompanyDO.builder()
                .address("地址").phone("123").bankName("行").bankAccount("账号").build();
        assertEquals("地址 123", FinanceCustomerCompanyService.composeBuyerAddressPhone(c));
        assertEquals("行 账号", FinanceCustomerCompanyService.composeBuyerBankAccount(c));
        assertNull(FinanceCustomerCompanyService.composeBuyerBankAccount(
                FinanceCustomerCompanyDO.builder().build()));
    }

    @Test
    void updateShouldPassNullOptionalFieldsToMapperForClear() {
        when(mapper.selectById(1L)).thenReturn(FinanceCustomerCompanyDO.builder()
                .id(1L)
                .code("CC-1")
                .name("客户甲")
                .taxNo("91110000TEST0001")
                .bankName("旧银行")
                .bankAccount("旧账号")
                .address("旧地址")
                .phone("旧电话")
                .isCustomer(true)
                .isSupplier(false)
                .status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                .build());
        when(mapper.selectByTaxNo("91110000TEST0001")).thenReturn(
                FinanceCustomerCompanyDO.builder().id(1L).taxNo("91110000TEST0001").build());

        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setId(1L);
        req.setName("客户甲");
        req.setTaxNo("91110000TEST0001");
        req.setIsCustomer(true);
        req.setIsSupplier(false);
        req.setBankName(null);
        req.setBankAccount("");
        req.setAddress(null);
        req.setPhone(null);

        service.updateCustomerCompany(req);

        verify(mapper).update(isNull(), any());
        verify(mapper, never()).updateById(any(FinanceCustomerCompanyDO.class));
    }

    @Test
    void purchaseProcessKeyWhitelist() {
        assertTrue(cn.iocoder.yudao.module.finance.enums.FinancePurchaseProcessConstants
                .isPurchaseProcessKey("oa_purchase_apply"));
        assertFalse(cn.iocoder.yudao.module.finance.enums.FinancePurchaseProcessConstants
                .isPurchaseProcessKey("oa_payment_apply"));
        assertEquals(1, cn.iocoder.yudao.module.finance.enums.FinancePurchaseProcessConstants
                .PURCHASE_PROCESS_KEYS.size());
    }

}
