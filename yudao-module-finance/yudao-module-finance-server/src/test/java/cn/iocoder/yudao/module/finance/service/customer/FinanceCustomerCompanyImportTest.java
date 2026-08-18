package cn.iocoder.yudao.module.finance.service.customer;

import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.customer.FinanceCustomerCompanyMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceCustomerCompanyNoRedisDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanceCustomerCompanyImportTest {

    private FinanceCustomerCompanyMapper mapper;
    private FinanceCustomerCompanyNoRedisDAO noRedisDAO;
    private FinanceCustomerCompanyServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceCustomerCompanyMapper.class);
        noRedisDAO = mock(FinanceCustomerCompanyNoRedisDAO.class);
        service = new FinanceCustomerCompanyServiceImpl(mapper, noRedisDAO);
        when(noRedisDAO.generate(any(LocalDate.class))).thenReturn("CC-20260818-1", "CC-20260818-2");
        AtomicLong ids = new AtomicLong(1);
        Map<Long, FinanceCustomerCompanyDO> store = new HashMap<>();
        doAnswer(inv -> {
            FinanceCustomerCompanyDO c = inv.getArgument(0);
            c.setId(ids.getAndIncrement());
            store.put(c.getId(), c);
            return 1;
        }).when(mapper).insert(any(FinanceCustomerCompanyDO.class));
        when(mapper.selectById(any())).thenAnswer(inv -> store.get(inv.getArgument(0)));
    }

    @Test
    void importValidRowShouldCreateAndReturnCode() {
        when(mapper.selectByTaxNo("91110000A")).thenReturn(null);
        FinanceCustomerCompanyImportRespVO resp = service.importCustomerCompanyList(List.of(validRow()));
        assertEquals(List.of("CC-20260818-1"), resp.getCreatedCodes());
        assertTrue(resp.getFailureRows().isEmpty());
        verify(mapper).insert(argThat((FinanceCustomerCompanyDO c) ->
                "客户甲".equals(c.getName())
                        && "91110000A".equals(c.getTaxNo())
                        && Boolean.TRUE.equals(c.getIsCustomer())
                        && Boolean.FALSE.equals(c.getIsSupplier())));
    }

    @Test
    void importExistingTaxNoShouldFailRow() {
        when(mapper.selectByTaxNo("91110000A")).thenReturn(FinanceCustomerCompanyDO.builder().id(9L).taxNo("91110000A").build());
        FinanceCustomerCompanyImportRespVO resp = service.importCustomerCompanyList(List.of(validRow()));
        assertTrue(resp.getCreatedCodes().isEmpty());
        assertEquals("纳税人识别号已存在", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceCustomerCompanyDO.class));
    }

    @Test
    void importDuplicateTaxNoInSameFileShouldFailLaterRow() {
        when(mapper.selectByTaxNo(any())).thenReturn(null);
        FinanceCustomerCompanyImportExcelVO second = validRow();
        second.setName("客户乙");
        FinanceCustomerCompanyImportRespVO resp = service.importCustomerCompanyList(List.of(validRow(), second));
        assertEquals(1, resp.getCreatedCodes().size());
        assertEquals("本文件内纳税人识别号重复", resp.getFailureRows().get(3));
    }

    @Test
    void importSupplierWithoutBankShouldFail() {
        FinanceCustomerCompanyImportExcelVO row = validRow();
        row.setIsCustomerText("否");
        row.setIsSupplierText("是");
        FinanceCustomerCompanyImportRespVO resp = service.importCustomerCompanyList(List.of(row));
        assertEquals("供应商角色启用时开户银行与银行账号不能为空", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceCustomerCompanyDO.class));
    }

    @Test
    void importNeitherRoleShouldFail() {
        FinanceCustomerCompanyImportExcelVO row = validRow();
        row.setIsCustomerText("否");
        row.setIsSupplierText("否");
        FinanceCustomerCompanyImportRespVO resp = service.importCustomerCompanyList(List.of(row));
        assertEquals("至少选择客户或供应商角色之一", resp.getFailureRows().get(2));
    }

    @Test
    void importEmptyListShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> service.importCustomerCompanyList(List.of()));
    }

    private static FinanceCustomerCompanyImportExcelVO validRow() {
        return FinanceCustomerCompanyImportExcelVO.builder()
                .name("客户甲")
                .taxNo("91110000A")
                .build();
    }
}
