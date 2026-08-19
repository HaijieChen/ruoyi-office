package cn.iocoder.yudao.module.finance.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - 部门利润表")
@Data
public class FinanceDeptProfitReportRespVO {

    private List<Row> list;
    private Row total;
    private Long excludedNonCnyCount;
    private BigDecimal salaryTaxPaidAmount;
    private BigDecimal salaryAllocationAmount;
    private BigDecimal unallocatedResidualAmount;

    @Data
    public static class Row {
        private Long deptId;
        private String deptName;
        private BigDecimal incomeAmount;
        private BigDecimal costAmount;
        private BigDecimal reimbursementAmount;
        private BigDecimal allocationAmount;
        private BigDecimal profitAmount;
        private boolean unallocated;
        private boolean totalRow;
    }
}
