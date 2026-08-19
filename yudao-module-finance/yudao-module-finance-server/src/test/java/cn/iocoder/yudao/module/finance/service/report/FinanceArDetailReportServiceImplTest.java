package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportPageRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceArDetailReportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.mysql.report.FinanceArDetailReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceArDetailReportServiceImplTest {

    private static final Long STAFF_ID = 100L;
    private static final Long OTHER_ID = 999L;

    private FinanceArDetailReportMapper mapper;
    private FinanceArDetailReportServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(FinanceArDetailReportMapper.class);
        service = new FinanceArDetailReportServiceImpl(mapper);
    }

    @Test
    void getPageShouldComputeFormulasForCnyRow() {
        when(mapper.selectReportList(any(), eq(STAFF_ID))).thenReturn(List.of(
                order(1L, STAFF_ID, "CNY", "100", "40", "10")));

        FinanceArDetailReportPageRespVO page = service.getPage(pageReq(), STAFF_ID, false);

        assertEquals(1L, page.getTotal());
        assertEquals(0L, page.getExcludedNonCnyCount());
        FinanceArDetailReportRespVO row = page.getList().get(0);
        assertEquals(0, new BigDecimal("60").compareTo(row.getUninvoicedAmount()));
        assertEquals(0, new BigDecimal("30").compareTo(row.getInvoicedArAmount()));
        assertEquals(0, new BigDecimal("90").compareTo(row.getArTotalAmount()));
        assertEquals("CNY", row.getCurrency());
    }

    @Test
    void staffCannotWidenImporterViaQueryParam() {
        FinanceArDetailReportPageReqVO reqVO = pageReq();
        reqVO.setImporterId(OTHER_ID);
        when(mapper.selectReportList(any(), eq(STAFF_ID))).thenReturn(List.of(
                order(1L, STAFF_ID, "CNY", "100", "40", "10")));

        service.getPage(reqVO, STAFF_ID, false);

        ArgumentCaptor<Long> importerCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mapper).selectReportList(any(), importerCaptor.capture());
        assertEquals(STAFF_ID, importerCaptor.getValue());
    }

    @Test
    void staffExportCannotWidenImporterViaQueryParam() {
        FinanceArDetailReportPageReqVO reqVO = pageReq();
        reqVO.setImporterId(OTHER_ID);
        when(mapper.selectReportList(any(), eq(STAFF_ID))).thenReturn(List.of());

        service.listForExport(reqVO, STAFF_ID, false);

        verify(mapper).selectReportList(any(), eq(STAFF_ID));
    }

    @Test
    void queryAllKeepsClientImporterFilter() {
        FinanceArDetailReportPageReqVO reqVO = pageReq();
        reqVO.setImporterId(OTHER_ID);
        when(mapper.selectReportList(any(), eq(OTHER_ID))).thenReturn(List.of());

        service.getPage(reqVO, STAFF_ID, true);

        verify(mapper).selectReportList(any(), eq(OTHER_ID));
    }

    @Test
    void queryAllWithoutImporterSeesAll() {
        when(mapper.selectReportList(any(), eq(null))).thenReturn(List.of());

        service.getPage(pageReq(), STAFF_ID, true);

        verify(mapper).selectReportList(any(), eq(null));
    }

    @Test
    void shouldExcludeNonCnyAndCountThem() {
        when(mapper.selectReportList(any(), eq(STAFF_ID))).thenReturn(List.of(
                order(1L, STAFF_ID, "cny", "100", "40", "10"),
                order(2L, STAFF_ID, "USD", "100", "40", "10"),
                order(3L, STAFF_ID, null, "100", "40", "10"),
                order(4L, STAFF_ID, "  ", "100", "40", "10"),
                order(5L, STAFF_ID, "HKD", "100", "40", "10")));

        FinanceArDetailReportPageRespVO page = service.getPage(pageReq(), STAFF_ID, false);

        assertEquals(1L, page.getTotal());
        assertEquals(4L, page.getExcludedNonCnyCount());
        assertEquals(1, page.getList().size());
        assertEquals(1L, page.getList().get(0).getId());
        assertEquals("CNY", page.getList().get(0).getCurrency());
    }

    @Test
    void bothFlagsTrueShouldBeIntersection() {
        when(mapper.selectReportList(any(), eq(STAFF_ID))).thenReturn(List.of(
                order(1L, STAFF_ID, "CNY", "100", "40", "10"),
                order(2L, STAFF_ID, "CNY", "100", "0", "0"),
                order(3L, STAFF_ID, "CNY", "100", "100", "70")));

        FinanceArDetailReportPageReqVO both = pageReq();
        both.setUninvoicedOnly(true);
        both.setInvoicedArOnly(true);
        List<Long> bothIds = service.getPage(both, STAFF_ID, false).getList()
                .stream().map(FinanceArDetailReportRespVO::getId).toList();
        assertEquals(List.of(1L), bothIds);

        FinanceArDetailReportPageReqVO uninvoiced = pageReq();
        uninvoiced.setUninvoicedOnly(true);
        List<Long> uninvoicedIds = service.getPage(uninvoiced, STAFF_ID, false).getList()
                .stream().map(FinanceArDetailReportRespVO::getId).toList();
        assertEquals(List.of(1L, 2L), uninvoicedIds);

        FinanceArDetailReportPageReqVO invoicedAr = pageReq();
        invoicedAr.setInvoicedArOnly(true);
        List<Long> invoicedArIds = service.getPage(invoicedAr, STAFF_ID, false).getList()
                .stream().map(FinanceArDetailReportRespVO::getId).toList();
        assertEquals(List.of(1L, 3L), invoicedArIds);
    }

    @Test
    void listForExportShouldReuseSameFiltersAndFormulas() {
        when(mapper.selectReportList(any(), eq(STAFF_ID))).thenReturn(List.of(
                order(1L, STAFF_ID, "CNY", "100", "40", "10"),
                order(2L, STAFF_ID, "USD", "100", "40", "10")));

        List<FinanceArDetailReportRespVO> rows = service.listForExport(pageReq(), STAFF_ID, false);

        assertEquals(1, rows.size());
        assertEquals(0, new BigDecimal("60").compareTo(rows.get(0).getUninvoicedAmount()));
        assertEquals("CNY", rows.get(0).getCurrency());
    }

    @Test
    void shouldFillProductTypeFromSnapshotThenName() {
        FinanceBusinessOrderDO snapshot = order(1L, STAFF_ID, "CNY", "100", "40", "10");
        snapshot.setProductTypeSnapshot(" 软件 ");
        snapshot.setProductName("旧名");
        FinanceBusinessOrderDO fallback = order(2L, STAFF_ID, "CNY", "100", "40", "10");
        fallback.setProductTypeSnapshot("  ");
        fallback.setProductName("旧名");
        when(mapper.selectReportList(any(), eq(STAFF_ID))).thenReturn(List.of(snapshot, fallback));

        List<FinanceArDetailReportRespVO> rows = service.getPage(pageReq(), STAFF_ID, false).getList();

        assertEquals("软件", rows.get(0).getProductType());
        assertEquals("旧名", rows.get(1).getProductType());
    }

    @Test
    void shouldKeepEntityCompanyAndOrderNoOnRow() {
        FinanceBusinessOrderDO order = order(8L, STAFF_ID, "CNY", "100", "40", "10");
        order.setOrderNo("BO-1");
        order.setEntityCompanyDeptId(20L);
        order.setEntityCompanyName("主体A");
        when(mapper.selectReportList(any(), eq(STAFF_ID))).thenReturn(List.of(order));

        FinanceArDetailReportRespVO row = service.getPage(pageReq(), STAFF_ID, false).getList().get(0);

        assertEquals("BO-1", row.getOrderNo());
        assertEquals(20L, row.getEntityCompanyDeptId());
        assertEquals("主体A", row.getEntityCompanyName());
        assertTrue(new BigDecimal("100").compareTo(row.getSettlementAmount()) == 0);
        assertTrue(new BigDecimal("40").compareTo(row.getInvoicedOccupiedAmount()) == 0);
        assertTrue(new BigDecimal("10").compareTo(row.getConfirmedClaimedAmount()) == 0);
    }

    private static FinanceArDetailReportPageReqVO pageReq() {
        FinanceArDetailReportPageReqVO reqVO = new FinanceArDetailReportPageReqVO();
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        return reqVO;
    }

    private static FinanceBusinessOrderDO order(Long id, Long importerId, String currency,
                                               String settlement, String occupied, String claimed) {
        return FinanceBusinessOrderDO.builder()
                .id(id)
                .importerId(importerId)
                .currency(currency)
                .settlementAmount(new BigDecimal(settlement))
                .invoicedOccupiedAmount(new BigDecimal(occupied))
                .confirmedClaimedAmount(new BigDecimal(claimed))
                .build();
    }
}
