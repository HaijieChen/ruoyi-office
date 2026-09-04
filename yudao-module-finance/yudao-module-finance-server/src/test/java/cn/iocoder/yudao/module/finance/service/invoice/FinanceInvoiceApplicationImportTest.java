package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.invoice.FinanceInvoiceApplicationMapper;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FinanceInvoiceApplicationImportTest {

    private FinanceInvoiceApplicationMapper applicationMapper;
    private FinanceInvoiceApplicationLineMapper lineMapper;
    private FinanceBusinessOrderMapper businessOrderMapper;
    private AdminUserApi adminUserApi;
    private FinanceEntityCompanyResolver entityCompanyResolver;
    private DictDataApi dictDataApi;
    private FinanceInvoiceApplicationImportServiceImpl service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(FinanceInvoiceApplicationMapper.class);
        lineMapper = mock(FinanceInvoiceApplicationLineMapper.class);
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        adminUserApi = mock(AdminUserApi.class);
        entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        dictDataApi = mock(DictDataApi.class);
        service = new FinanceInvoiceApplicationImportServiceImpl(
                applicationMapper, lineMapper, businessOrderMapper, adminUserApi,
                entityCompanyResolver, dictDataApi);
        when(entityCompanyResolver.loadEnabledCompanies()).thenReturn(List.of());
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

    @Test
    void importPersistsCompanyProductAndIssueTime() {
        when(entityCompanyResolver.matchByNameOrError(any(), any(), any())).thenAnswer(inv -> {
            FinanceEntityCompanyResolver.ResolvedCompany[] out = inv.getArgument(1);
            out[0] = new FinanceEntityCompanyResolver.ResolvedCompany(10L, "示例主体公司");
            return null;
        });
        when(dictDataApi.validateDictDataList(eq("finance_product_type"), anyCollection()))
                .thenReturn(CommonResult.success(true));
        FinanceInvoiceApplicationImportExcelVO row = validRow();
        row.setInvoiceCompany("示例主体公司");
        row.setProductType("广告");
        row.setIssueTime("2026-01-15");
        row.setInvoiceNo("12345678");
        FinanceInvoiceApplicationImportRespVO resp = service.importHistorical(List.of(row));
        assertEquals(List.of("INV-H-1"), resp.getCreatedNos());
        ArgumentCaptor<FinanceInvoiceApplicationDO> appCap =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).insert(appCap.capture());
        assertEquals("示例主体公司", appCap.getValue().getInvoiceCompany());
        assertEquals(10L, appCap.getValue().getInvoiceCompanyDeptId());
        assertEquals("广告", appCap.getValue().getTaxContent());
        ArgumentCaptor<FinanceInvoiceApplicationLineDO> lineCap =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationLineDO.class);
        verify(lineMapper).insert(lineCap.capture());
        assertEquals("广告", lineCap.getValue().getProductTypeSnapshot());
        assertEquals("12345678", lineCap.getValue().getInvoiceNo());
        assertEquals(2026, lineCap.getValue().getIssuedAt().getYear());
        assertEquals(1, lineCap.getValue().getIssuedAt().getMonthValue());
        assertEquals(15, lineCap.getValue().getIssuedAt().getDayOfMonth());
    }

    @Test
    void importPersistsRemark() {
        FinanceInvoiceApplicationImportExcelVO row = validRow();
        row.setRemark("历史备注");
        FinanceInvoiceApplicationImportRespVO resp = service.importHistorical(List.of(row));
        assertEquals(List.of("INV-H-1"), resp.getCreatedNos());
        ArgumentCaptor<FinanceInvoiceApplicationDO> cap =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).insert(cap.capture());
        assertEquals("历史备注", cap.getValue().getRemark());
    }

    @Test
    void importBlankRemarkDoesNotCopyInvoiceNo() {
        FinanceInvoiceApplicationImportExcelVO row = validRow();
        row.setInvoiceNo("12345678");
        FinanceInvoiceApplicationImportRespVO resp = service.importHistorical(List.of(row));
        assertEquals(List.of("INV-H-1"), resp.getCreatedNos());
        ArgumentCaptor<FinanceInvoiceApplicationDO> appCap =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationDO.class);
        verify(applicationMapper).insert(appCap.capture());
        assertNull(appCap.getValue().getRemark());
        ArgumentCaptor<FinanceInvoiceApplicationLineDO> lineCap =
                ArgumentCaptor.forClass(FinanceInvoiceApplicationLineDO.class);
        verify(lineMapper).insert(lineCap.capture());
        assertEquals("12345678", lineCap.getValue().getInvoiceNo());
    }

    @Test
    void oversizedRemarkFailsRowWithoutBlockingNeighbors() {
        FinanceInvoiceApplicationImportExcelVO bad = validRow();
        bad.setApplicationNo("INV-H-BAD");
        bad.setRemark("x".repeat(501));
        FinanceInvoiceApplicationImportExcelVO ok = validRow();
        ok.setApplicationNo("INV-H-OK");
        ok.setRemark("短备注");
        FinanceInvoiceApplicationImportRespVO resp = service.importHistorical(List.of(bad, ok));
        assertEquals(List.of("INV-H-OK"), resp.getCreatedNos());
        assertEquals("备注长度不能超过 500", resp.getFailureRows().get(2));
        verify(applicationMapper, times(1)).insert(any(FinanceInvoiceApplicationDO.class));
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
