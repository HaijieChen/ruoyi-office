package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.controller.admin.report.vo.FinanceDeptProfitReportRespVO;

import java.math.BigDecimal;

public final class FinanceDeptProfitCalculator {

    public static final long UNALLOCATED_DEPT_ID = -1L;
    public static final String UNALLOCATED_DEPT_NAME = "未分摊";

    private FinanceDeptProfitCalculator() {
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal profit(BigDecimal income, BigDecimal cost,
                                    BigDecimal reimbursement, BigDecimal allocation) {
        return nz(income).subtract(nz(cost)).subtract(nz(reimbursement)).subtract(nz(allocation));
    }

    public static BigDecimal unallocatedResidual(BigDecimal salaryTaxPaid, BigDecimal salaryAllocation) {
        BigDecimal residual = nz(salaryTaxPaid).subtract(nz(salaryAllocation));
        return residual.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : residual;
    }

    public static FinanceDeptProfitReportRespVO.Row row(Long deptId, String deptName,
                                                        BigDecimal income, BigDecimal cost,
                                                        BigDecimal reimbursement, BigDecimal allocation) {
        FinanceDeptProfitReportRespVO.Row row = new FinanceDeptProfitReportRespVO.Row();
        row.setDeptId(deptId);
        row.setDeptName(deptName);
        row.setIncomeAmount(nz(income));
        row.setCostAmount(nz(cost));
        row.setReimbursementAmount(nz(reimbursement));
        row.setAllocationAmount(nz(allocation));
        row.setProfitAmount(profit(income, cost, reimbursement, allocation));
        row.setUnallocated(false);
        row.setTotalRow(false);
        return row;
    }

    public static FinanceDeptProfitReportRespVO.Row unallocatedRow(BigDecimal residual) {
        FinanceDeptProfitReportRespVO.Row row = row(
                UNALLOCATED_DEPT_ID, UNALLOCATED_DEPT_NAME,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, residual);
        row.setUnallocated(true);
        row.setProfitAmount(residual.negate());
        return row;
    }
}
