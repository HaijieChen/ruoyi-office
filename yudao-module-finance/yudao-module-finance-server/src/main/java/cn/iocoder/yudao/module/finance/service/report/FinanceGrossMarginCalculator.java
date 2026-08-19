package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceGrossMarginReportRespVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FinanceGrossMarginCalculator {

    public static final long UNASSIGNED_DEPT_ID = 0L;
    public static final String UNASSIGNED_DEPT_NAME = "未分配";
    public static final String UNCLASSIFIED_PRODUCT = "未分类";

    private FinanceGrossMarginCalculator() {
    }

    public static boolean isCny(String currency) {
        return FinanceArDetailCalculator.isCny(currency);
    }

    public static String yearMonth(LocalDate date) {
        if (date == null) {
            return null;
        }
        return YearMonth.from(date).toString();
    }

    public static Long deptOrUnassigned(Long deptId) {
        return deptId == null ? UNASSIGNED_DEPT_ID : deptId;
    }

    public static String productOrUnclassified(String productType) {
        if (productType == null || productType.isBlank()) {
            return UNCLASSIFIED_PRODUCT;
        }
        return productType.trim();
    }

    public static String key(String yearMonth, Long deptId, String productType) {
        return yearMonth + "|" + deptId + "|" + productType;
    }

    public static List<FinanceGrossMarginReportRespVO> toSortedRows(
            Map<String, FinanceGrossMarginReportRespVO> acc) {
        List<FinanceGrossMarginReportRespVO> rows = new ArrayList<>(acc.values());
        rows.sort(Comparator
                .comparing(FinanceGrossMarginReportRespVO::getYearMonth)
                .thenComparing(FinanceGrossMarginReportRespVO::getDeptId)
                .thenComparing(FinanceGrossMarginReportRespVO::getProductType));
        return rows;
    }

    public static FinanceGrossMarginReportRespVO row(
            String yearMonth, Long deptId, String deptName, String productType) {
        FinanceGrossMarginReportRespVO row = new FinanceGrossMarginReportRespVO();
        row.setYearMonth(yearMonth);
        row.setDeptId(deptId);
        row.setDeptName(deptId.equals(UNASSIGNED_DEPT_ID) ? UNASSIGNED_DEPT_NAME : deptName);
        row.setProductType(productType);
        row.setIncomeAmount(BigDecimal.ZERO);
        row.setCostAmount(BigDecimal.ZERO);
        row.setMarginAmount(BigDecimal.ZERO);
        return row;
    }

    public static void addIncome(FinanceGrossMarginReportRespVO row, BigDecimal amount) {
        row.setIncomeAmount(nz(row.getIncomeAmount()).add(nz(amount)));
        row.setMarginAmount(row.getIncomeAmount().subtract(nz(row.getCostAmount())));
    }

    public static void addCost(FinanceGrossMarginReportRespVO row, BigDecimal amount) {
        row.setCostAmount(nz(row.getCostAmount()).add(nz(amount)));
        row.setMarginAmount(nz(row.getIncomeAmount()).subtract(row.getCostAmount()));
    }

    public static Map<String, FinanceGrossMarginReportRespVO> newAcc() {
        return new LinkedHashMap<>();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
