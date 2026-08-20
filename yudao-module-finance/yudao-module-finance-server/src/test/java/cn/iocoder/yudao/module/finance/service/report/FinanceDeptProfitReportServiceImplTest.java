package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceDeptProfitReportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.allocation.FinanceDeptCostAllocationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.allocation.FinanceDeptCostAllocationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class FinanceDeptProfitReportServiceImplTest {

    private FinanceDeptProfitReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FinanceDeptProfitReportServiceImpl(
                mock(FinanceGrossMarginReportService.class),
                mock(FinanceDeptCostAllocationMapper.class),
                mock(FinancePaymentPayLineMapper.class),
                mock(FinancePaymentApplicationMapper.class),
                mock(cn.iocoder.yudao.module.finance.service.fx.FinanceExchangeRateService.class));
    }

    @Test
    void profitIsIncomeMinusCostMinusAllocation() {
        FinanceGrossMarginReportRespVO gm = new FinanceGrossMarginReportRespVO();
        gm.setDeptId(10L);
        gm.setDeptName("研发");
        gm.setIncomeAmount(new BigDecimal("100"));
        gm.setCostAmount(new BigDecimal("40"));
        FinanceDeptCostAllocationDO alloc = FinanceDeptCostAllocationDO.builder()
                .deptId(10L)
                .deptName("研发")
                .sourceType(FinanceDeptCostAllocationDO.SOURCE_CLOUD)
                .amount(new BigDecimal("10"))
                .build();
        FinanceDeptProfitReportRespVO resp = service.aggregate(
                List.of(gm), List.of(alloc), BigDecimal.ZERO, 0L);
        FinanceDeptProfitReportRespVO.Row dept = resp.getList().get(0);
        assertEquals(new BigDecimal("50"), dept.getProfitAmount());
        assertEquals(new BigDecimal("50"), resp.getTotal().getProfitAmount());
    }

    @Test
    void unallocatedOnlySubtractsSalaryAllocations() {
        FinanceDeptCostAllocationDO salary = FinanceDeptCostAllocationDO.builder()
                .deptId(10L)
                .sourceType(FinanceDeptCostAllocationDO.SOURCE_SALARY)
                .amount(new BigDecimal("50"))
                .build();
        FinanceDeptCostAllocationDO cloud = FinanceDeptCostAllocationDO.builder()
                .deptId(10L)
                .sourceType(FinanceDeptCostAllocationDO.SOURCE_CLOUD)
                .amount(new BigDecimal("20"))
                .build();
        FinanceDeptProfitReportRespVO resp = service.aggregate(
                List.of(), List.of(salary, cloud), new BigDecimal("80"), 0L);
        assertEquals(new BigDecimal("30"), resp.getUnallocatedResidualAmount());
        FinanceDeptProfitReportRespVO.Row last = resp.getList().get(resp.getList().size() - 1);
        assertEquals(true, last.isUnallocated());
        assertEquals(new BigDecimal("30"), last.getAllocationAmount());
    }
}
