package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceRedflushCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceRedflushDO;

public interface FinanceInvoiceRedflushService {

    String PROCESS_KEY = "finance_invoice_redflush_apply";

    Long createAndStart(FinanceInvoiceRedflushCreateAndStartReqVO reqVO, Long applicantUserId);

    void resubmit(Long id, FinanceInvoiceRedflushCreateAndStartReqVO reqVO, Long userId);

    /**
     * 仅解锁原单，不释放商务单占用。
     */
    void onApprovalOutcome(Long redflushId, String outcome);

    void completeIssue(Long redflushId, FinanceInvoiceApplicationCompleteIssueReqVO reqVO);

    FinanceInvoiceRedflushDO get(Long id);
}
