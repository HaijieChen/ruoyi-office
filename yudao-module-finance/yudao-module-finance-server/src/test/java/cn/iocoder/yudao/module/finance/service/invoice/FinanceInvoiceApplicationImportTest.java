package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FinanceInvoiceApplicationImportTest {

    private FinanceInvoiceApplicationMapper applicationMapper;
    private FinanceInvoiceApplicationLineMapper lineMapper;
    private FinanceBusinessOrderMapper businessOrderMapper;
    private AdminUserApi adminUserApi;
    private FinanceInvoiceApplicationImportServiceImpl service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(FinanceInvoiceApplicationMapper.class);
        lineMapper = mock(FinanceInvoiceApplicationLineMapper.class);
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        adminUserApi = mock(AdminUserApi.class);
        service = new FinanceInvoiceApplicationImportServiceImpl(
                applicationMapper, lineMapper, businessOrderMapper, adminUserApi);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(88L);
        user.setStatus(0);
        when(adminUserApi.getUserByUsername("bizuser")).thenReturn(CommonResult.success(user));
        when(applicationMapper.selectByApplicationNo(anyString())).thenReturn(null);
        AtomicLong ids = new AtomicLong(1);
        doAnswer(inv -> {
            FinanceInvoiceApplicationDO app = inv.getArgument(0);
            app.setId(ids.getAndIncrement());
            return 1;
        }).when(applicationMapper).insert(any(FinanceInvoiceApplicationDO.class));
    }

    @Test
    void importValidRowApprovedAndIssued() {
        FinanceInvoiceApplicationImportRespVO resp = service.importHistorical(List.of(validRow()));
        assertEquals(List.of("INV-H-1"), resp.getCreatedNos());
        assertTrue(resp.getFailureRows().isEmpty());
        ArgumentCaptor<FinanceInvoiceApplicationDO> cap = ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).insert(cap.capture());
        assertEquals(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus(), cap.getValue().getApprovalStatus());
        assertEquals(FinanceInvoiceIssueStatusEnum.FULL.getStatus(), cap.getValue().getIssueStatus());
        assertNull(cap.getValue().getProcessInstanceId());
    }

    @Test
    void duplicateNoFails() {
        when(applicationMapper.selectByApplicationNo("INV-H-1"))
                .thenReturn(FinanceInvoiceApplicationDO.builder().id(9L).applicationNo("INV-H-1").build());
        FinanceInvoiceApplicationImportRespVO resp = service.importHistorical(List.of(validRow()));
        assertTrue(resp.getCreatedNos().isEmpty());
        assertEquals("开票申请单号已存在", resp.getFailureRows().get(2));
        verify(applicationMapper, never()).insert(any(FinanceInvoiceApplicationDO.class));
    }

    @Test
    void fileDuplicateFails() {
        FinanceInvoiceApplicationImportRespVO resp = service.importHistorical(List.of(validRow(), validRow()));
        assertEquals(1, resp.getCreatedNos().size());
        assertEquals("本文件内开票申请单号重复", resp.getFailureRows().get(3));
    }

    private static FinanceInvoiceApplicationImportExcelVO validRow() {
        return FinanceInvoiceApplicationImportExcelVO.builder()
                .applicationNo("INV-H-1")
                .applicantUsername("bizuser")
                .buyerName("客户甲")
                .totalAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .build();
    }
}
