package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;

import java.math.BigDecimal;

/**
 * 红冲前置开票申请是否可选（R1）。不复用认领 claimAllowed（后者不读 issue_status）。
 */
public final class FinanceInvoiceRedflushEligibility {

    private FinanceInvoiceRedflushEligibility() {
    }

    public static boolean isSelectablePredecessor(FinanceInvoiceApplicationDO app) {
        if (app == null) {
            return false;
        }
        if (!FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus().equals(app.getApprovalStatus())) {
            return false;
        }
        if (!Integer.valueOf(FinanceInvoiceIssueStatusEnum.FULL.getStatus()).equals(app.getIssueStatus())) {
            return false;
        }
        if (Boolean.TRUE.equals(app.getVoided())) {
            return false;
        }
        if (Boolean.TRUE.equals(app.getRedFlushed())) {
            return false;
        }
        if (app.getRedFlushLockApplicationId() != null) {
            return false;
        }
        BigDecimal pending = app.getPendingClaimedAmount() == null ? BigDecimal.ZERO : app.getPendingClaimedAmount();
        BigDecimal confirmed = app.getConfirmedClaimedAmount() == null ? BigDecimal.ZERO : app.getConfirmedClaimedAmount();
        return pending.add(confirmed).compareTo(BigDecimal.ZERO) <= 0;
    }
}
