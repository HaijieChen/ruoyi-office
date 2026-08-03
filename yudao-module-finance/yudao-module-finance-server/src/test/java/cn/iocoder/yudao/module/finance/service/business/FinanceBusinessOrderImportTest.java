package cn.iocoder.yudao.module.finance.service.business;

import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportExcelVO;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.contract.FinanceContractApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.redis.no.FinanceBusinessOrderNoRedisDAO;
import cn.iocoder.yudao.module.finance.enums.FinanceContractApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.service.common.FinanceEntityCompanyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Row-based 商务签单信息 Excel import + settlement calculation.
 * One spreadsheet row -> one finance_business_order.
 * 主体公司由 Excel「主体公司」列名称匹配组织架构公司。
 */
class FinanceBusinessOrderImportTest {

    private static final Long IMPORTER_ID = 100L;
    private static final Long CONTRACT_APP_ID = 50L;
    private static final Long ENTITY_COMPANY_DEPT_ID = 10L;
    private static final String ENTITY_COMPANY_NAME = "示例主体公司";

    private FinanceBusinessOrderMapper businessOrderMapper;
    private FinanceBusinessOrderNoRedisDAO businessOrderNoRedisDAO;
    private FinanceContractApplicationMapper contractApplicationMapper;
    private FinanceEntityCompanyResolver entityCompanyResolver;
    private FinanceBusinessOrderServiceImpl businessOrderService;

    @BeforeEach
    void setUp() {
        businessOrderMapper = mock(FinanceBusinessOrderMapper.class);
        businessOrderNoRedisDAO = mock(FinanceBusinessOrderNoRedisDAO.class);
        contractApplicationMapper = mock(FinanceContractApplicationMapper.class);
        entityCompanyResolver = mock(FinanceEntityCompanyResolver.class);
        businessOrderService = new FinanceBusinessOrderServiceImpl(
                businessOrderMapper, businessOrderNoRedisDAO, contractApplicationMapper, entityCompanyResolver);
        when(businessOrderNoRedisDAO.generate(any(LocalDate.class))).thenReturn("BO-20260723-1");
        when(contractApplicationMapper.selectByApplicationNo(anyString())).thenReturn(
                FinanceContractApplicationDO.builder()
                        .id(CONTRACT_APP_ID)
                        .applicationNo("CT-001")
                        .approvalStatus(FinanceContractApprovalStatusEnum.APPROVED.getStatus())
                        .applicantUserId(IMPORTER_ID)
                        .voided(false)
                        .build());
        when(entityCompanyResolver.loadEnabledCompanies()).thenReturn(List.of());
        when(entityCompanyResolver.matchByNameOrError(anyString(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    FinanceEntityCompanyResolver.ResolvedCompany[] out = invocation.getArgument(1);
                    out[0] = new FinanceEntityCompanyResolver.ResolvedCompany(
                            ENTITY_COMPANY_DEPT_ID, ENTITY_COMPANY_NAME);
                    return null;
                });
    }

    @Test
    void importBusinessOrderListShouldPersistOneActiveOrderPerValidRow() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        when(businessOrderMapper.selectBySourceRowHash(anyString())).thenReturn(null);

        FinanceBusinessOrderImportRespVO respVO = businessOrderService.importBusinessOrderList(
                List.of(row), IMPORTER_ID);

        assertEquals(1, respVO.getOrderNos().size());
        assertTrue(respVO.getFailureRows().isEmpty());
        assertTrue(respVO.getSkippedRows().isEmpty());

        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) ->
                "BO-20260723-1".equals(order.getOrderNo())
                        && "PROC-001".equals(order.getContractProcessId())
                        && CONTRACT_APP_ID.equals(order.getContractApplicationId())
                        && LocalDate.now().equals(order.getImportDate())
                        && IMPORTER_ID.equals(order.getImporterId())
                        && order.getPayerName() == null
                        && ENTITY_COMPANY_DEPT_ID.equals(order.getEntityCompanyDeptId())
                        && ENTITY_COMPANY_NAME.equals(order.getEntityCompanyName())
                        && new BigDecimal("1000.00").compareTo(order.getSignedExecutionAmount()) == 0
                        && new BigDecimal("0.10").compareTo(order.getDiscountRate()) == 0
                        && new BigDecimal("900.00").compareTo(order.getSettlementAmount()) == 0
                        && "备注内容".equals(order.getRemark())
                         && order.getSourceRowHash() != null));
    }

    @Test
    void importBusinessOrderListShouldPersistOptionalPayer() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        row.setPayerName("付款公司");
        when(businessOrderMapper.selectBySourceRowHash(anyString())).thenReturn(null);

        businessOrderService.importBusinessOrderList(List.of(row), IMPORTER_ID);

        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) ->
                "付款公司".equals(order.getPayerName())));
    }

    @Test
    void importBusinessOrderListShouldTreatBlankDiscountAsZero() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        row.setDiscountRate(null);
        when(businessOrderMapper.selectBySourceRowHash(anyString())).thenReturn(null);

        businessOrderService.importBusinessOrderList(List.of(row), IMPORTER_ID);

        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) ->
                BigDecimal.ZERO.compareTo(order.getDiscountRate()) == 0
                        && new BigDecimal("1000.00").compareTo(order.getSettlementAmount()) == 0));
    }

    @Test
    void importBusinessOrderListShouldRoundSettlementHalfUpToScale2() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        row.setSignedExecutionAmount(new BigDecimal("1000.005"));
        row.setDiscountRate(new BigDecimal("0.001"));
        when(businessOrderMapper.selectBySourceRowHash(anyString())).thenReturn(null);

        businessOrderService.importBusinessOrderList(List.of(row), IMPORTER_ID);

        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) ->
                new BigDecimal("1000.01").equals(order.getSignedExecutionAmount())
                        && new BigDecimal("0.001000").equals(order.getDiscountRate())
                        && new BigDecimal("999.01").equals(order.getSettlementAmount())));
    }

    @Test
    void importBusinessOrderListShouldReportMissingRequiredFields() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        row.setProductName(null);
        row.setContactPerson(null);
        row.setOrderDate(null);
        row.setExecutionStartDate(null);
        row.setExecutionEndDate(null);
        row.setSignedExecutionAmount(null);

        FinanceBusinessOrderImportRespVO respVO = businessOrderService.importBusinessOrderList(
                List.of(row), IMPORTER_ID);

        assertEquals(1, respVO.getFailureRows().size());
        assertTrue(respVO.getFailureRows().containsKey(2));
        assertTrue(respVO.getOrderNos().isEmpty());
        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void importBusinessOrderListShouldRequireEntityCompany() {
        when(entityCompanyResolver.matchByNameOrError(any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn("主体公司不能为空");

        FinanceBusinessOrderImportExcelVO row = validRow();
        row.setEntityCompanyName(" ");

        FinanceBusinessOrderImportRespVO respVO = businessOrderService.importBusinessOrderList(
                List.of(row), IMPORTER_ID);

        assertEquals(1, respVO.getFailureRows().size());
        assertTrue(respVO.getFailureRows().get(2).contains("主体公司"));
        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void importBusinessOrderListShouldRequireRows() {
        assertThrows(IllegalArgumentException.class,
                () -> businessOrderService.importBusinessOrderList(List.of(), IMPORTER_ID));

        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void importBusinessOrderListShouldRejectDiscountOutsideZeroToOne() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        row.setDiscountRate(new BigDecimal("1.01"));

        FinanceBusinessOrderImportRespVO respVO = businessOrderService.importBusinessOrderList(
                List.of(row), IMPORTER_ID);

        assertEquals(1, respVO.getFailureRows().size());
        assertTrue(respVO.getFailureRows().get(2).contains("折扣率"));
        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void importBusinessOrderListShouldRejectExecutionEndBeforeStart() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        row.setExecutionStartDate(LocalDate.of(2026, 7, 20));
        row.setExecutionEndDate(LocalDate.of(2026, 7, 10));

        FinanceBusinessOrderImportRespVO respVO = businessOrderService.importBusinessOrderList(
                List.of(row), IMPORTER_ID);

        assertEquals(1, respVO.getFailureRows().size());
        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void importBusinessOrderListShouldAcceptBlankContractProcessId() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        row.setContractProcessId(null);
        when(businessOrderMapper.selectBySourceRowHash(anyString())).thenReturn(null);

        FinanceBusinessOrderImportRespVO respVO = businessOrderService.importBusinessOrderList(
                List.of(row), IMPORTER_ID);

        assertEquals(1, respVO.getOrderNos().size());
        assertTrue(respVO.getFailureRows().isEmpty());
        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) -> order.getContractProcessId() == null));
    }

    @Test
    void importBusinessOrderListShouldSkipDuplicateRowsWithinSameFile() {
        FinanceBusinessOrderImportExcelVO row1 = validRow();
        FinanceBusinessOrderImportExcelVO row2 = validRow();
        when(businessOrderMapper.selectBySourceRowHash(anyString())).thenReturn(null);

        FinanceBusinessOrderImportRespVO respVO = businessOrderService.importBusinessOrderList(
                List.of(row1, row2), IMPORTER_ID);

        assertEquals(1, respVO.getOrderNos().size());
        assertEquals(List.of(3), respVO.getSkippedRows());
        verify(businessOrderMapper, times(1)).insert(any(FinanceBusinessOrderDO.class));
    }

    @Test
    void importBusinessOrderListShouldHashAndPersistDatabaseNormalizedDecimals() {
        FinanceBusinessOrderImportExcelVO row1 = validRow();
        row1.setSignedExecutionAmount(new BigDecimal("1000.004"));
        row1.setDiscountRate(new BigDecimal("0.1000004"));
        FinanceBusinessOrderImportExcelVO row2 = validRow();
        row2.setSignedExecutionAmount(new BigDecimal("1000.003"));
        row2.setDiscountRate(new BigDecimal("0.1000003"));
        when(businessOrderMapper.selectBySourceRowHash(anyString())).thenReturn(null);

        FinanceBusinessOrderImportRespVO response = businessOrderService.importBusinessOrderList(
                List.of(row1, row2), IMPORTER_ID);

        assertEquals(1, response.getOrderNos().size());
        assertEquals(List.of(3), response.getSkippedRows());
        verify(businessOrderMapper).insert(argThat((FinanceBusinessOrderDO order) ->
                new BigDecimal("1000.00").equals(order.getSignedExecutionAmount())
                        && new BigDecimal("0.100000").equals(order.getDiscountRate())
                        && new BigDecimal("900.00").equals(order.getSettlementAmount())));
    }

    @Test
    void importBusinessOrderListShouldSkipRowsAlreadyImportedForTenant() {
        FinanceBusinessOrderImportExcelVO row = validRow();
        when(businessOrderMapper.selectBySourceRowHash(anyString()))
                .thenReturn(FinanceBusinessOrderDO.builder().id(1L).orderNo("BO-EXISTING").build());

        FinanceBusinessOrderImportRespVO respVO = businessOrderService.importBusinessOrderList(
                List.of(row), IMPORTER_ID);

        assertTrue(respVO.getOrderNos().isEmpty());
        assertEquals(List.of(2), respVO.getSkippedRows());
        verify(businessOrderMapper, never()).insert(any(FinanceBusinessOrderDO.class));
    }

    private static FinanceBusinessOrderImportExcelVO validRow() {
        return FinanceBusinessOrderImportExcelVO.builder()
                .entityCompanyName(ENTITY_COMPANY_NAME)
                .contractProcessId("PROC-001")
                .contractApplicationNo("CT-001")
                .orderDate(LocalDate.of(2026, 7, 1))
                .productName("产品A")
                .contactPerson("张三")
                .executionStartDate(LocalDate.of(2026, 7, 5))
                .executionEndDate(LocalDate.of(2026, 7, 31))
                .payerName(null)
                .signedExecutionAmount(new BigDecimal("1000.00"))
                .discountRate(new BigDecimal("0.10"))
                .summary("备注内容")
                .build();
    }

}
