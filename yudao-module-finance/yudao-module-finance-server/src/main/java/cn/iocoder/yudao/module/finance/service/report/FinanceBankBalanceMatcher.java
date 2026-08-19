package cn.iocoder.yudao.module.finance.service.report;

import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public final class FinanceBankBalanceMatcher {

    private FinanceBankBalanceMatcher() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace(" ", "").trim();
    }

    public static boolean matchesAccount(FinanceReceiptDO receipt, FinanceCompanyBankAccountDO account) {
        if (receipt == null || account == null) {
            return false;
        }
        if (!Objects.equals(receipt.getEntityCompanyDeptId(), account.getEntityCompanyDeptId())) {
            return false;
        }
        String token = normalize(receipt.getBankAccount());
        if (token.isEmpty()) {
            return false;
        }
        return token.equals(normalize(account.getAccountNo()))
                || token.equals(normalize(account.getAccountName()));
    }

    public static boolean inWindow(LocalDate movement, LocalDate openingAsOfExclusive, LocalDate asOfInclusive) {
        if (movement == null || asOfInclusive == null) {
            return false;
        }
        if (movement.isAfter(asOfInclusive)) {
            return false;
        }
        return openingAsOfExclusive == null || movement.isAfter(openingAsOfExclusive);
    }

    public static LocalDate toLocalDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
    }
}
