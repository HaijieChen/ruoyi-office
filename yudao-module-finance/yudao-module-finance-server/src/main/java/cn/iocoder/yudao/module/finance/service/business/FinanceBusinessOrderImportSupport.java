package cn.iocoder.yudao.module.finance.service.business;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderImportExcelVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

final class FinanceBusinessOrderImportSupport {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal ZERO_DISCOUNT = new BigDecimal("0.000000");

    private FinanceBusinessOrderImportSupport() {
    }

    static NormalizedAmounts normalizeAmounts(BigDecimal executionAmount, BigDecimal discountRate) {
        BigDecimal normalizedExecutionAmount = executionAmount == null ? null
                : executionAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedDiscountRate = discountRate == null ? ZERO_DISCOUNT
                : discountRate.setScale(6, RoundingMode.HALF_UP);
        BigDecimal settlementAmount = normalizedExecutionAmount == null ? null
                : calculateSettlementAmount(normalizedExecutionAmount, normalizedDiscountRate);
        return new NormalizedAmounts(normalizedExecutionAmount, normalizedDiscountRate, settlementAmount);
    }

    static String validateRow(FinanceBusinessOrderImportExcelVO row, NormalizedAmounts amounts) {
        if (row == null) {
            return "导入行不能为空";
        }
        if (StrUtil.isBlank(row.getEntityCompanyName())) {
            return "主体公司不能为空";
        }
        if (row.getOrderDate() == null) {
            return "下单日期不能为空";
        }
        if (StrUtil.isBlank(row.getProductName())) {
            return "产品名称不能为空";
        }
        if (StrUtil.isBlank(row.getContactPerson())) {
            return "对接人不能为空";
        }
        if (row.getExecutionStartDate() == null || row.getExecutionEndDate() == null) {
            return "执行开始日和执行截止日不能为空";
        }
        if (row.getExecutionEndDate().isBefore(row.getExecutionStartDate())) {
            return "执行截止日不能早于执行开始日";
        }
        if (amounts.signedExecutionAmount() == null
                || amounts.signedExecutionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "签单执行金额必须大于 0";
        }
        if (amounts.discountRate().compareTo(BigDecimal.ZERO) < 0
                || amounts.discountRate().compareTo(BigDecimal.ONE) > 0) {
            return "折扣率必须在 0 到 1 之间";
        }
        if (StrUtil.isBlank(row.getContractApplicationNo())) {
            return "合同申请业务单号不能为空";
        }
        return null;
    }

    static BigDecimal calculateSettlementAmount(BigDecimal executionAmount, BigDecimal discountRate) {
        return executionAmount.multiply(BigDecimal.ONE.subtract(discountRate)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 幂等键：不含 bank_account；含主体公司 deptId（C2）。
     */
    static String calculateSourceRowHash(FinanceBusinessOrderImportExcelVO row, NormalizedAmounts amounts,
                                         Long entityCompanyDeptId) {
        String canonicalRow = String.join("\u001f",
                normalize(row.getContractProcessId()), row.getOrderDate().toString(), normalize(row.getProductName()),
                normalize(row.getContactPerson()), row.getExecutionStartDate().toString(),
                row.getExecutionEndDate().toString(), normalize(row.getPayerName()),
                normalize(amounts.signedExecutionAmount()), normalize(amounts.discountRate()),
                normalize(row.getSummary()),
                entityCompanyDeptId == null ? "" : entityCompanyDeptId.toString());
        return DigestUtil.sha256Hex(canonicalRow);
    }

    static FinanceBusinessOrderDO buildOrder(FinanceBusinessOrderImportExcelVO row, Long importerId,
                                             Long entityCompanyDeptId, String entityCompanyName,
                                             NormalizedAmounts amounts,
                                             String sourceRowHash, String orderNo, Long contractApplicationId) {
        return FinanceBusinessOrderDO.builder()
                .orderNo(orderNo).importDate(LocalDate.now()).importerId(importerId)
                .entityCompanyDeptId(entityCompanyDeptId)
                .entityCompanyName(entityCompanyName)
                .contractProcessId(trimToNull(row.getContractProcessId()))
                .contractApplicationId(contractApplicationId)
                .remark(trimToNull(row.getSummary()))
                .confirmedClaimedAmount(ZERO)
                .orderDate(row.getOrderDate()).productName(row.getProductName().trim())
                .contactPerson(row.getContactPerson().trim()).executionStartDate(row.getExecutionStartDate())
                .executionEndDate(row.getExecutionEndDate()).payerName(trimToNull(row.getPayerName()))
                .signedExecutionAmount(amounts.signedExecutionAmount()).discountRate(amounts.discountRate())
                .settlementAmount(amounts.settlementAmount())
                .sourceRowHash(sourceRowHash).build();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String trimToNull(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    record NormalizedAmounts(BigDecimal signedExecutionAmount, BigDecimal discountRate,
                             BigDecimal settlementAmount) {
    }
}
