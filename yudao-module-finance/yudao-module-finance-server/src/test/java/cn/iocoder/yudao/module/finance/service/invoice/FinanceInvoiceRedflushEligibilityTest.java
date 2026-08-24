package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceApprovalStatusEnum;
import cn.iocoder.yudao.module.finance.enums.FinanceInvoiceIssueStatusEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceInvoiceRedflushEligibilityTest {

    @Test
    void eligibleFullyIssuedUnclaimedUnlocked() {
        assertTrue(FinanceInvoiceRedflushEligibility.isSelectablePredecessor(base()));
    }

    @Test
    void claimedPendingHidden() {
        FinanceInvoiceApplicationDO app = base();
        app.setPendingClaimedAmount(new BigDecimal("1.00"));
        assertFalse(FinanceInvoiceRedflushEligibility.isSelectablePredecessor(app));
    }

    @Test
    void claimedConfirmedHidden() {
        FinanceInvoiceApplicationDO app = base();
        app.setConfirmedClaimedAmount(new BigDecimal("10.00"));
        assertFalse(FinanceInvoiceRedflushEligibility.isSelectablePredecessor(app));
    }

    @Test
    void approvedButNotIssuedHidden() {
        FinanceInvoiceApplicationDO app = base();
        app.setIssueStatus(FinanceInvoiceIssueStatusEnum.NONE.getStatus());
        assertFalse(FinanceInvoiceRedflushEligibility.isSelectablePredecessor(app));
    }

    @Test
    void alreadyRedFlushedHidden() {
        FinanceInvoiceApplicationDO app = base();
        app.setRedFlushed(Boolean.TRUE);
        assertFalse(FinanceInvoiceRedflushEligibility.isSelectablePredecessor(app));
    }

    @Test
    void lockedHidden() {
        FinanceInvoiceApplicationDO app = base();
        app.setRedFlushLockApplicationId(99L);
        assertFalse(FinanceInvoiceRedflushEligibility.isSelectablePredecessor(app));
    }

    private static FinanceInvoiceApplicationDO base() {
        return FinanceInvoiceApplicationDO.builder()
                .id(1L)
                .approvalStatus(FinanceInvoiceApprovalStatusEnum.APPROVED.getStatus())
                .issueStatus(FinanceInvoiceIssueStatusEnum.FULL.getStatus())
                .voided(Boolean.FALSE)
                .redFlushed(Boolean.FALSE)
                .pendingClaimedAmount(BigDecimal.ZERO)
                .confirmedClaimedAmount(BigDecimal.ZERO)
                .build();
    }
}
