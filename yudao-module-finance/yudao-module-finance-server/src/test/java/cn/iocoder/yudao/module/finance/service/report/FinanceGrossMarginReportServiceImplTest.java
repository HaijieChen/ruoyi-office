package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.business.FinanceBusinessOrderMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceGrossMarginReportServiceImplTest {

    private FinanceGrossMarginReportServiceImpl service;
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUp() {
        adminUserApi = mock(AdminUserApi.class);
        cn.iocoder.yudao.module.finance.service.fx.FinanceExchangeRateService fx =
                mock(cn.iocoder.yudao.module.finance.service.fx.FinanceExchangeRateService.class);
        when(fx.toCny(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            java.math.BigDecimal a = inv.getArgument(0);
            return a == null ? java.math.BigDecimal.ZERO : a;
        });
        service = new FinanceGrossMarginReportServiceImpl(
                mock(FinanceBusinessOrderMapper.class),
                mock(FinancePaymentPayLineMapper.class),
                mock(FinancePaymentApplicationMapper.class),
                adminUserApi,
                fx);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(1L);
        user.setDeptId(10L);
        when(adminUserApi.getUser(anyLong())).thenReturn(CommonResult.success(user));
    }

    @Test
    void sameMonthDeptProductAggregatesIncomeCostMargin() {
        FinanceBusinessOrderDO order = FinanceBusinessOrderDO.builder()
                .importerId(1L)
                .orderDate(LocalDate.of(2026, 8, 2))
                .settlementAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .productTypeSnapshot("软件")
                .build();
        FinancePaymentApplicationDO payment = FinancePaymentApplicationDO.builder()
                .id(8L)
                .applicationKind("ORDINARY")
                .applicantUserId(1L)
                .costProject("软件")
                .currency("CNY")
                .build();
        FinancePaymentPayLineDO line = FinancePaymentPayLineDO.builder()
                .paymentApplicationId(8L)
                .actualPayDate(LocalDate.of(2026, 8, 20))
                .payAmount(new BigDecimal("40.00"))
                .currencySnapshot("CNY")
                .build();
        FinanceGrossMarginReportPageReqVO req = new FinanceGrossMarginReportPageReqVO();
        req.setFromMonth("2026-08");
        req.setToMonth("2026-08");
        var result = service.aggregate(List.of(order), List.of(line), Map.of(8L, payment), req);
        assertEquals(1, result.rows().size());
        FinanceGrossMarginReportRespVO row = result.rows().get(0);
        assertEquals("2026-08", row.getYearMonth());
        assertEquals(10L, row.getDeptId());
        assertEquals("软件", row.getProductType());
        assertEquals(new BigDecimal("100.00"), row.getIncomeAmount());
        assertEquals(new BigDecimal("40.00"), row.getCostAmount());
        assertEquals(new BigDecimal("60.00"), row.getMarginAmount());
        assertEquals(0L, result.excludedNonCnyCount());
    }

    @Test
    void differentProductDoesNotMix() {
        FinanceBusinessOrderDO order = FinanceBusinessOrderDO.builder()
                .importerId(1L)
                .orderDate(LocalDate.of(2026, 8, 2))
                .settlementAmount(new BigDecimal("100.00"))
                .currency("CNY")
                .productTypeSnapshot("软件")
                .build();
        FinancePaymentApplicationDO payment = FinancePaymentApplicationDO.builder()
                .id(8L)
                .applicationKind("ORDINARY")
                .applicantUserId(1L)
                .costProject("搜索")
                .currency("CNY")
                .build();
        FinancePaymentPayLineDO line = FinancePaymentPayLineDO.builder()
                .paymentApplicationId(8L)
                .actualPayDate(LocalDate.of(2026, 8, 20))
                .payAmount(new BigDecimal("40.00"))
                .currencySnapshot("CNY")
                .build();
        var result = service.aggregate(List.of(order), List.of(line), Map.of(8L, payment),
                new FinanceGrossMarginReportPageReqVO());
        assertEquals(2, result.rows().size());
    }

    @Test
    void usdBusinessOrderConverted() {
        FinanceBusinessOrderDO order = FinanceBusinessOrderDO.builder()
                .importerId(1L)
                .orderDate(LocalDate.of(2026, 8, 2))
                .settlementAmount(new BigDecimal("100.00"))
                .currency("USD")
                .productTypeSnapshot("软件")
                .build();
        var result = service.aggregate(List.of(order), List.of(), Map.of(),
                new FinanceGrossMarginReportPageReqVO());
        assertEquals(1, result.rows().size());
        assertEquals(0L, result.excludedNonCnyCount());
    }

    @Test
    void salaryPaymentNotInCost() {
        FinancePaymentApplicationDO payment = FinancePaymentApplicationDO.builder()
                .id(8L)
                .applicationKind("SALARY")
                .applicantUserId(1L)
                .costProject("软件")
                .currency("CNY")
                .build();
        FinancePaymentPayLineDO line = FinancePaymentPayLineDO.builder()
                .paymentApplicationId(8L)
                .actualPayDate(LocalDate.of(2026, 8, 20))
                .payAmount(new BigDecimal("40.00"))
                .currencySnapshot("CNY")
                .build();
        var result = service.aggregate(List.of(), List.of(line), Map.of(8L, payment),
                new FinanceGrossMarginReportPageReqVO());
        assertEquals(0, result.rows().size());
    }
}
