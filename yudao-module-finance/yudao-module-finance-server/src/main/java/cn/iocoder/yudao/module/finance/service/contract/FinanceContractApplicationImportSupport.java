package cn.iocoder.yudao.module.finance.service.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationImportExcelVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

final class FinanceContractApplicationImportSupport {

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "采购合同", "销售合同", "付款业务合同", "租赁合同", "借款合同");
    private static final Set<String> SALES_LIKE_FILE_TYPES = Set.of("销售合同", "付款业务合同");
    private static final Set<String> ALLOWED_SETTLEMENT_METHODS = Set.of(
            "CPA", "CPS", "CPC", "月结", "其他");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private FinanceContractApplicationImportSupport() {
    }

    record ParsedRow(String applicationNo,
                     String applicantUsername,
                     String entityCompanyName,
                     String counterpartyName,
                     String fileType,
                     String productType,
                     boolean amountNa,
                     BigDecimal contractAmount,
                     String rebateRatio,
                     String settlementMethod,
                     String fileName,
                     LocalDate startDate,
                     LocalDate endDate) {
    }

    static String validateAndParse(FinanceContractApplicationImportExcelVO row, ParsedRow[] out) {
        if (row == null) {
            return "导入行不能为空";
        }
        String applicationNo = trimToNull(row.getApplicationNo());
        String applicantUsername = trimToNull(row.getApplicantUsername());
        if (applicantUsername == null) {
            return "申请人账号不能为空";
        }
        String entityCompanyName = trimToNull(row.getEntityCompanyName());
        if (entityCompanyName == null) {
            return "签约主体不能为空";
        }
        String counterpartyName = trimToNull(row.getCounterpartyName());
        if (counterpartyName == null) {
            return "对方客商不能为空";
        }
        String fileType = trimToNull(row.getFileType());
        if (fileType == null || !ALLOWED_FILE_TYPES.contains(fileType)) {
            return "合同类型必须是 采购合同/销售合同/付款业务合同/租赁合同/借款合同";
        }
        boolean sales = SALES_LIKE_FILE_TYPES.contains(fileType);
        String productType = trimToNull(row.getProductType());
        if (sales && productType == null) {
            return "产品类型不能为空";
        }
        if (!sales) {
            productType = null;
        }
        Boolean applicable;
        try {
            applicable = parseRequiredYesNo(row.getAmountApplicableText());
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        BigDecimal contractAmount = row.getContractAmount();
        boolean amountNa;
        if (Boolean.TRUE.equals(applicable)) {
            if (contractAmount == null || contractAmount.compareTo(ZERO) <= 0) {
                return "金额适用时合同金额必须大于0";
            }
            amountNa = false;
        } else {
            if (contractAmount != null) {
                return "金额不适用时合同金额必须为空";
            }
            amountNa = true;
        }
        String rebateRatio = trimToNull(row.getRebateRatio());
        String settlementMethod = trimToNull(row.getSettlementMethod());
        if (sales) {
            if (rebateRatio == null) {
                return "返点比例不能为空";
            }
            if (settlementMethod == null || !ALLOWED_SETTLEMENT_METHODS.contains(settlementMethod)) {
                return "结算方式必须是 CPA/CPS/CPC/月结/其他";
            }
        } else {
            rebateRatio = null;
            settlementMethod = null;
        }
        String fileName = trimToNull(row.getFileName());
        if (fileName == null) {
            return "文件名称不能为空";
        }
        LocalDate startDate = row.getStartDate();
        LocalDate endDate = row.getEndDate();
        if (startDate == null || endDate == null) {
            return "起始日期、结束日期不能为空";
        }
        if (endDate.isBefore(startDate)) {
            return "结束日期不能早于起始日期";
        }
        out[0] = new ParsedRow(applicationNo, applicantUsername, entityCompanyName, counterpartyName,
                fileType, productType, amountNa, amountNa ? null : contractAmount, rebateRatio,
                settlementMethod, fileName, startDate, endDate);
        return null;
    }

    static Boolean parseRequiredYesNo(String raw) {
        if (StrUtil.isBlank(raw)) {
            throw new IllegalArgumentException("金额是否适用只允许是/否");
        }
        String v = raw.trim();
        if ("是".equals(v)) {
            return true;
        }
        if ("否".equals(v)) {
            return false;
        }
        throw new IllegalArgumentException("金额是否适用只允许是/否");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
