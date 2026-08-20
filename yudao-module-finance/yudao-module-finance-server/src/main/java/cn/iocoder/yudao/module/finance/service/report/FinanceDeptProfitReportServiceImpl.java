package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceDeptProfitReportReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceDeptProfitReportRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportRespVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.allocation.FinanceDeptCostAllocationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO;
import cn.iocoder.yudao.module.finance.dal.mysql.allocation.FinanceDeptCostAllocationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentPayLineMapper;
import cn.iocoder.yudao.module.finance.service.fx.FinanceExchangeRateService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Validated
public class FinanceDeptProfitReportServiceImpl implements FinanceDeptProfitReportService {

    private final FinanceGrossMarginReportService grossMarginReportService;
    private final FinanceDeptCostAllocationMapper allocationMapper;
    private final FinancePaymentPayLineMapper payLineMapper;
    private final FinancePaymentApplicationMapper paymentApplicationMapper;
    private final FinanceExchangeRateService exchangeRateService;

    public FinanceDeptProfitReportServiceImpl(FinanceGrossMarginReportService grossMarginReportService,
                                              FinanceDeptCostAllocationMapper allocationMapper,
                                              FinancePaymentPayLineMapper payLineMapper,
                                              FinancePaymentApplicationMapper paymentApplicationMapper,
                                              FinanceExchangeRateService exchangeRateService) {
        this.grossMarginReportService = grossMarginReportService;
        this.allocationMapper = allocationMapper;
        this.payLineMapper = payLineMapper;
        this.paymentApplicationMapper = paymentApplicationMapper;
        this.exchangeRateService = exchangeRateService;
    }

    @Override
    public FinanceDeptProfitReportRespVO query(FinanceDeptProfitReportReqVO reqVO) {
        FinanceGrossMarginReportPageReqVO gmReq = new FinanceGrossMarginReportPageReqVO();
        gmReq.setFromMonth(reqVO.getFromMonth());
        gmReq.setToMonth(reqVO.getToMonth());
        List<FinanceGrossMarginReportRespVO> gmRows = grossMarginReportService.listForExport(gmReq);
        List<FinanceDeptCostAllocationDO> allocations = allocationMapper.selectList(
                new LambdaQueryWrapperX<FinanceDeptCostAllocationDO>()
                        .geIfPresent(FinanceDeptCostAllocationDO::getPeriod, reqVO.getFromMonth())
                        .leIfPresent(FinanceDeptCostAllocationDO::getPeriod, reqVO.getToMonth()));
        BigDecimal salaryTaxPaid = sumSalaryTaxPaid(reqVO);
        return aggregate(gmRows, allocations, salaryTaxPaid, 0L);
    }

    FinanceDeptProfitReportRespVO aggregate(List<FinanceGrossMarginReportRespVO> gmRows,
                                            List<FinanceDeptCostAllocationDO> allocations,
                                            BigDecimal salaryTaxPaid,
                                            long excludedNonCnyCount) {
        Map<Long, FinanceDeptProfitReportRespVO.Row> byDept = new HashMap<>();
        for (FinanceGrossMarginReportRespVO gm : gmRows) {
            FinanceDeptProfitReportRespVO.Row row = byDept.computeIfAbsent(gm.getDeptId(),
                    id -> FinanceDeptProfitCalculator.row(id, gm.getDeptName(),
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            row.setIncomeAmount(FinanceDeptProfitCalculator.nz(row.getIncomeAmount())
                    .add(FinanceDeptProfitCalculator.nz(gm.getIncomeAmount())));
            row.setCostAmount(FinanceDeptProfitCalculator.nz(row.getCostAmount())
                    .add(FinanceDeptProfitCalculator.nz(gm.getCostAmount())));
            if (row.getDeptName() == null) {
                row.setDeptName(gm.getDeptName());
            }
        }
        BigDecimal salaryAlloc = BigDecimal.ZERO;
        for (FinanceDeptCostAllocationDO alloc : allocations) {
            FinanceDeptProfitReportRespVO.Row row = byDept.computeIfAbsent(alloc.getDeptId(),
                    id -> FinanceDeptProfitCalculator.row(id, alloc.getDeptName(),
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            row.setAllocationAmount(FinanceDeptProfitCalculator.nz(row.getAllocationAmount())
                    .add(FinanceDeptProfitCalculator.nz(alloc.getAmount())));
            if (FinanceDeptCostAllocationDO.SOURCE_SALARY.equals(alloc.getSourceType())) {
                salaryAlloc = salaryAlloc.add(FinanceDeptProfitCalculator.nz(alloc.getAmount()));
            }
        }
        for (FinanceDeptProfitReportRespVO.Row row : byDept.values()) {
            row.setReimbursementAmount(BigDecimal.ZERO);
            row.setProfitAmount(FinanceDeptProfitCalculator.profit(
                    row.getIncomeAmount(), row.getCostAmount(),
                    row.getReimbursementAmount(), row.getAllocationAmount()));
        }
        List<FinanceDeptProfitReportRespVO.Row> rows = new ArrayList<>(byDept.values());
        rows.sort(Comparator.comparing(FinanceDeptProfitReportRespVO.Row::getDeptId));
        BigDecimal residual = FinanceDeptProfitCalculator.unallocatedResidual(salaryTaxPaid, salaryAlloc);
        FinanceDeptProfitReportRespVO.Row unallocated = FinanceDeptProfitCalculator.unallocatedRow(residual);
        rows.add(unallocated);

        FinanceDeptProfitReportRespVO.Row total = FinanceDeptProfitCalculator.row(
                null, "合计", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        total.setTotalRow(true);
        for (FinanceDeptProfitReportRespVO.Row row : rows) {
            if (row.isUnallocated()) {
                continue;
            }
            total.setIncomeAmount(total.getIncomeAmount().add(row.getIncomeAmount()));
            total.setCostAmount(total.getCostAmount().add(row.getCostAmount()));
            total.setReimbursementAmount(total.getReimbursementAmount().add(row.getReimbursementAmount()));
            total.setAllocationAmount(total.getAllocationAmount().add(row.getAllocationAmount()));
        }
        total.setProfitAmount(FinanceDeptProfitCalculator.profit(
                total.getIncomeAmount(), total.getCostAmount(),
                total.getReimbursementAmount(), total.getAllocationAmount()));

        FinanceDeptProfitReportRespVO resp = new FinanceDeptProfitReportRespVO();
        resp.setList(rows);
        resp.setTotal(total);
        resp.setExcludedNonCnyCount(excludedNonCnyCount);
        resp.setSalaryTaxPaidAmount(FinanceDeptProfitCalculator.nz(salaryTaxPaid));
        resp.setSalaryAllocationAmount(salaryAlloc);
        resp.setUnallocatedResidualAmount(residual);
        return resp;
    }

    private BigDecimal sumSalaryTaxPaid(FinanceDeptProfitReportReqVO reqVO) {
        LocalDate from = parseMonthStart(reqVO.getFromMonth());
        LocalDate to = parseMonthEnd(reqVO.getToMonth());
        List<FinancePaymentPayLineDO> lines = payLineMapper.selectList(
                new LambdaQueryWrapperX<FinancePaymentPayLineDO>()
                        .geIfPresent(FinancePaymentPayLineDO::getActualPayDate, from)
                        .leIfPresent(FinancePaymentPayLineDO::getActualPayDate, to));
        Set<Long> ids = new HashSet<>();
        for (FinancePaymentPayLineDO line : lines) {
            if (line.getPaymentApplicationId() != null) {
                ids.add(line.getPaymentApplicationId());
            }
        }
        if (ids.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, FinancePaymentApplicationDO> payments = new HashMap<>();
        for (FinancePaymentApplicationDO payment : paymentApplicationMapper.selectBatchIds(ids)) {
            payments.put(payment.getId(), payment);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (FinancePaymentPayLineDO line : lines) {
            FinancePaymentApplicationDO payment = payments.get(line.getPaymentApplicationId());
            if (payment == null || !isSalaryOrTax(payment.getApplicationKind())) {
                continue;
            }
            sum = sum.add(exchangeRateService.toCny(
                    line.getPayAmount(),
                    line.getCurrencySnapshot() != null ? line.getCurrencySnapshot() : payment.getCurrency(),
                    line.getActualPayDate()));
        }
        return sum;
    }

    private static boolean isSalaryOrTax(String kind) {
        return "SALARY".equalsIgnoreCase(kind) || "TAX".equalsIgnoreCase(kind);
    }

    private static LocalDate parseMonthStart(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        return YearMonth.parse(month.trim()).atDay(1);
    }

    private static LocalDate parseMonthEnd(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        return YearMonth.parse(month.trim()).atEndOfMonth();
    }
}
