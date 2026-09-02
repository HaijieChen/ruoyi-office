package cn.iocoder.yudao.module.finance.service.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceContractApplicationNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import cn.iocoder.yudao.module.finance.service.customer.FinanceCustomerCompanyService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FinanceContractApplicationImportTest {

    private FinanceContractApplicationMapper mapper;
    private FinanceCustomerCompanyService customerCompanyService;
    private FinanceEntityCompanyResolver entityCompanyResolver;
    private AdminUserApi adminUserApi;
    private DictDataApi dictDataApi;
    private FinanceContractApplicationNoRedisDAO applicationNoRedisDAO;
    private FinanceContractApplicationImportServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceContractApplicationMapper.class);
        customerCompanyService = mock(FinanceCustomerCompanyService.class);
        entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        adminUserApi = mock(AdminUserApi.class);
        dictDataApi = mock(DictDataApi.class);
        applicationNoRedisDAO = mock(FinanceContractApplicationNoRedisDAO.class);
        service = new FinanceContractApplicationImportServiceImpl(
                mapper, customerCompanyService, entityCompanyResolver, adminUserApi, dictDataApi,
                applicationNoRedisDAO);

        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(88L);
        user.setStatus(0);
        user.setDeptId(3L);
        when(adminUserApi.getUserByUsername("bizuser")).thenReturn(CommonResult.success(user));

        when(customerCompanyService.getEnabledSimpleList()).thenReturn(List.of(
                FinanceCustomerCompanyDO.builder().id(7L).name("客户甲").isCustomer(true).status(0).build()));

        when(entityCompanyResolver.loadEnabledCompanies()).thenReturn(List.of());
        when(entityCompanyResolver.matchByNameOrError(any(), any(), any())).thenAnswer(inv -> {
            FinanceEntityCompanyResolver.ResolvedCompany[] out = inv.getArgument(1);
            out[0] = new FinanceEntityCompanyResolver.ResolvedCompany(10L, "示例主体");
            return null;
        });

        when(dictDataApi.validateDictDataList(eq("finance_product_type"), anyCollection()))
                .thenReturn(CommonResult.success(true));
        when(mapper.selectByApplicationNo(anyString())).thenReturn(null);
        AtomicInteger seq = new AtomicInteger(1);
        when(applicationNoRedisDAO.generate(any(LocalDate.class)))
                .thenAnswer(inv -> "CT-20260902-" + seq.getAndIncrement());

        AtomicLong ids = new AtomicLong(1);
        doAnswer(inv -> {
            FinanceContractApplicationDO app = inv.getArgument(0);
            app.setId(ids.getAndIncrement());
            return 1;
        }).when(mapper).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void importValidRowShouldPersistApprovedWithoutProcess() {
        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(validRow()));

        assertEquals(List.of("HT-HIST-001"), resp.getCreatedNos());
        assertTrue(resp.getFailureRows().isEmpty());

        ArgumentCaptor<FinanceContractApplicationDO> captor =
                ArgumentCaptor.forClass(FinanceContractApplicationDO.class);
        verify(mapper).insert(captor.capture());
        FinanceContractApplicationDO saved = captor.getValue();
        assertEquals(FinanceContractApprovalStatusEnum.APPROVED.getStatus(), saved.getApprovalStatus());
        assertNull(saved.getProcessInstanceId());
        assertEquals(Boolean.FALSE, saved.getVoided());
        assertEquals(88L, saved.getApplicantUserId());
        assertEquals(7L, saved.getCounterpartyCompanyId());
        assertEquals(Boolean.FALSE, saved.getNeedMail());
        assertEquals(1, saved.getCopyCount());
        assertEquals("CNY", saved.getCurrency());
        assertEquals(Boolean.FALSE, saved.getAmountNa());
        assertEquals(0, new BigDecimal("1000").compareTo(saved.getContractAmount()));
        verify(applicationNoRedisDAO, never()).generate(any(LocalDate.class));
    }

    @Test
    void missingCounterpartyShouldFail() {
        when(customerCompanyService.getEnabledSimpleList()).thenReturn(List.of());

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(validRow()));

        assertTrue(resp.getCreatedNos().isEmpty());
        assertEquals("对方客商未找到或未启用", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void duplicateCounterpartyNameShouldFail() {
        when(customerCompanyService.getEnabledSimpleList()).thenReturn(List.of(
                FinanceCustomerCompanyDO.builder().id(7L).name("客户甲").build(),
                FinanceCustomerCompanyDO.builder().id(8L).name("客户甲").build()));

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(validRow()));

        assertEquals("对方客商名称匹配到多家，请先改成唯一名称", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void unknownApplicantShouldFail() {
        when(adminUserApi.getUserByUsername("bizuser")).thenReturn(CommonResult.success(null));

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(validRow()));

        assertEquals("申请人账号不存在", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void disabledApplicantShouldFail() {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(88L);
        user.setStatus(1);
        user.setDeptId(3L);
        when(adminUserApi.getUserByUsername("bizuser")).thenReturn(CommonResult.success(user));

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(validRow()));

        assertEquals("申请人账号已停用", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void amountRequiredWhenApplicableShouldFail() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setAmountApplicableText("是");
        row.setContractAmount(null);

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertEquals("金额适用时合同金额必须大于0", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void amountForbiddenWhenNaShouldFail() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setAmountApplicableText("否");
        row.setContractAmount(new BigDecimal("1000.00"));

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertEquals("金额不适用时合同金额必须为空", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void blankApplicationNoShouldAutoGenerate() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setApplicationNo(null);

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertTrue(resp.getFailureRows().isEmpty());
        assertEquals(List.of("CT-20260902-1"), resp.getCreatedNos());
        verify(applicationNoRedisDAO).generate(any(LocalDate.class));
    }

    @Test
    void whitespaceApplicationNoShouldAutoGenerate() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setApplicationNo("   ");

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertTrue(resp.getFailureRows().isEmpty());
        assertEquals(List.of("CT-20260902-1"), resp.getCreatedNos());
    }

    @Test
    void twoBlankRowsShouldGenerateDistinctNos() {
        FinanceContractApplicationImportExcelVO first = validRow();
        first.setApplicationNo(null);
        FinanceContractApplicationImportExcelVO second = validRow();
        second.setApplicationNo("");

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(first, second));

        assertTrue(resp.getFailureRows().isEmpty());
        assertEquals(List.of("CT-20260902-1", "CT-20260902-2"), resp.getCreatedNos());
        verify(applicationNoRedisDAO, times(2)).generate(any(LocalDate.class));
    }

    @Test
    void invalidBlankRowShouldNotGenerate() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setApplicationNo(null);
        row.setApplicantUsername(null);

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertEquals("申请人账号不能为空", resp.getFailureRows().get(2));
        verify(applicationNoRedisDAO, never()).generate(any(LocalDate.class));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void existingApplicationNoShouldFail() {
        when(mapper.selectByApplicationNo("HT-HIST-001"))
                .thenReturn(FinanceContractApplicationDO.builder().id(9L).applicationNo("HT-HIST-001").build());

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(validRow()));

        assertEquals("合同业务单号已存在", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void duplicateNoInFileShouldFail() {
        FinanceContractApplicationImportExcelVO second = validRow();
        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(validRow(), second));

        assertEquals(List.of("HT-HIST-001"), resp.getCreatedNos());
        assertEquals("本文件内合同业务单号重复", resp.getFailureRows().get(3));
    }

    @Test
    void endBeforeStartShouldFail() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setStartDate(LocalDate.of(2026, 12, 31));
        row.setEndDate(LocalDate.of(2026, 1, 1));

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertEquals("结束日期不能早于起始日期", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void illegalFileTypeShouldFail() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setFileType("未知类型");

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertEquals("合同类型必须是 采购合同/销售合同/付款业务合同/租赁合同/借款合同/推广充值业务合同/其他，当前「未知类型」",
                resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    @Test
    void otherFileTypeShouldImport() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setFileType("其他");

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertTrue(resp.getFailureRows().isEmpty());
        assertEquals(1, resp.getCreatedNos().size());
        ArgumentCaptor<FinanceContractApplicationDO> captor =
                ArgumentCaptor.forClass(FinanceContractApplicationDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("其他", captor.getValue().getFileType());
    }

    @Test
    void entityCompanyResolverErrorShouldFail() {
        doReturn("主体公司不存在或未启用")
                .when(entityCompanyResolver).matchByNameOrError(any(), any(), any());

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(validRow()));

        assertEquals("主体公司不存在或未启用", resp.getFailureRows().get(2));
        verify(mapper, never()).insert(any(FinanceContractApplicationDO.class));
    }

    private static FinanceContractApplicationImportExcelVO validRow() {
        return FinanceContractApplicationImportExcelVO.builder()
                .applicationNo("HT-HIST-001")
                .applicantUsername("bizuser")
                .entityCompanyName("示例主体")
                .counterpartyName("客户甲")
                .fileType("销售合同")
                .productType("软件")
                .amountApplicableText("是")
                .contractAmount(new BigDecimal("1000.00"))
                .rebateRatio("10%")
                .settlementMethod("月结")
                .fileName("销售合同.pdf")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();
    }

    @Test
    void purchaseWithoutSalesFieldsShouldImport() {
        FinanceContractApplicationImportExcelVO row = validRow();
        row.setFileType("采购合同");
        row.setProductType(null);
        row.setRebateRatio(null);
        row.setSettlementMethod(null);

        FinanceContractApplicationImportRespVO resp = service.importApprovedList(List.of(row));

        assertEquals(List.of("HT-HIST-001"), resp.getCreatedNos());
        assertTrue(resp.getFailureRows().isEmpty());
        verify(mapper).insert(argThat((FinanceContractApplicationDO app) ->
                "采购合同".equals(app.getFileType()) && app.getProductType() == null));
        verify(dictDataApi, never()).validateDictDataList(anyString(), anyCollection());
    }
}
