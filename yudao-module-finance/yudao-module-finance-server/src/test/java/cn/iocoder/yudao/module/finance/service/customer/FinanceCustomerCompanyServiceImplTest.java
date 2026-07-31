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

import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.CUSTOMER_COMPANY_TAX_NO_EXISTS;
import static cn.iocoder.yudao.module.finance.enums.ErrorCodeConstants.INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    void createWithNameAndTaxOnlyShouldSucceed() {
        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setName("客户甲");
        req.setTaxNo("91110000TEST0001");
        Long id = service.createCustomerCompany(req);
        assertEquals(1L, id);
        verify(mapper).insert(any(FinanceCustomerCompanyDO.class));
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
        // 应用层唯一校验通过，insert 时并发撞 uk
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
                .id(1L).code("CC-1").name("客户甲").taxNo("OLD").status(0).build());
        when(mapper.selectByTaxNo("NEWTAX")).thenReturn(null);
        doThrow(new DuplicateKeyException("Duplicate entry for key 'uk_tax_no_tenant_deleted'"))
                .when(mapper).updateById(any(FinanceCustomerCompanyDO.class));

        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setId(1L);
        req.setName("客户甲");
        req.setTaxNo("NEWTAX");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateCustomerCompany(req));
        assertEquals(CUSTOMER_COMPANY_TAX_NO_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void getEnabledShouldRejectDisabled() {
        when(mapper.selectById(2L)).thenReturn(FinanceCustomerCompanyDO.builder()
                .id(2L).status(FinanceCustomerCompanyDO.STATUS_DISABLE).build());
        ServiceException ex = assertThrows(ServiceException.class, () -> service.getEnabledCustomerCompany(2L));
        assertEquals(INVOICE_APPLICATION_CUSTOMER_COMPANY_DISABLED.getCode(), ex.getCode());
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
                .status(FinanceCustomerCompanyDO.STATUS_ENABLE)
                .build());
        when(mapper.selectByTaxNo("91110000TEST0001")).thenReturn(
                FinanceCustomerCompanyDO.builder().id(1L).taxNo("91110000TEST0001").build());

        FinanceCustomerCompanySaveReqVO req = new FinanceCustomerCompanySaveReqVO();
        req.setId(1L);
        req.setName("客户甲");
        req.setTaxNo("91110000TEST0001");
        // 清空可选字段
        req.setBankName(null);
        req.setBankAccount("");
        req.setAddress(null);
        req.setPhone(null);

        service.updateCustomerCompany(req);

        ArgumentCaptor<FinanceCustomerCompanyDO> captor =
                ArgumentCaptor.forClass(FinanceCustomerCompanyDO.class);
        verify(mapper).updateById(captor.capture());
        FinanceCustomerCompanyDO updated = captor.getValue();
        assertEquals(1L, updated.getId());
        assertEquals("CC-1", updated.getCode());
        assertNull(updated.getBankName());
        assertNull(updated.getBankAccount());
        assertNull(updated.getAddress());
        assertNull(updated.getPhone());
    }

}
