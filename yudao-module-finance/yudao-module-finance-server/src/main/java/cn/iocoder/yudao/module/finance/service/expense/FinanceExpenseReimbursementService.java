package cn.iocoder.yudao.module.finance.service.expense;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseApproveReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseRecordPayReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementCreateReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementRespVO;

public interface FinanceExpenseReimbursementService {

    String PROCESS_KEY = "oa_expense_reimbursement";
    String PROCESS_KEY_NO_INVOICE = "oa_expense_no_invoice";
    String QUERY_PERMISSION = "finance:expense:query";

    Long create(FinanceExpenseReimbursementCreateReqVO reqVO, Long userId);

    Long createNoInvoice(FinanceExpenseReimbursementCreateReqVO reqVO, Long userId);

    FinanceExpenseReimbursementRespVO get(Long id, Long userId, boolean canQueryAll);

    PageResult<FinanceExpenseReimbursementRespVO> getPage(FinanceExpenseReimbursementPageReqVO reqVO,
                                                          Long userId, boolean canQueryAll);

    void approve(FinanceExpenseApproveReqVO reqVO, Long userId);

    void recordPay(FinanceExpenseRecordPayReqVO reqVO, Long userId);

    boolean invoiceNoUsed(String invoiceNo);

    java.util.List<String> listOccupiedPredocProcessInstanceIds();

    /**
     * BPM 终态回写。仅 REJECTED / CANCELLED；APPROVE 不改占用。
     * 已是该终态则幂等返回；已 PAID 禁止改 REJECTED/CANCELLED。
     */
    void onApprovalOutcome(Long id, String outcome, String processInstanceId);
}
