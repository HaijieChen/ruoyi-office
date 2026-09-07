package cn.iocoder.yudao.module.finance.service.payment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentRecordPayReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentPayLineRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentSalaryLineRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentTaxLineRespVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceSalaryPaymentCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinanceTaxPaymentCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;

import java.math.BigDecimal;
import java.util.List;

public interface FinancePaymentApplicationService {

    String PROCESS_KEY = "finance_payment_apply";
    String PROCESS_KEY_SALARY = "finance_salary_payment_apply";
    String PROCESS_KEY_TAX = "finance_tax_payment_apply";
    String TASK_CASHIER = "taskCashier";
    String TASK_FINANCE = "taskFinance";
    String TASK_DEPT_HEAD = "taskDeptHead";

    Long createAndStart(FinancePaymentApplicationCreateAndStartReqVO reqVO, Long applicantUserId);

    /** 薪资付款独立入口 */
    Long createAndStartSalary(FinanceSalaryPaymentCreateAndStartReqVO reqVO, Long applicantUserId);

    /** 税金付款独立入口 */
    Long createAndStartTax(FinanceTaxPaymentCreateAndStartReqVO reqVO, Long applicantUserId);

    /** 薪资付款驳回后重提：明细重写 + 新 PI + 清支付证据 */
    void resubmitSalary(Long id, FinanceSalaryPaymentCreateAndStartReqVO reqVO, Long userId);

    /** 税金付款驳回后重提：明细重写 + 新 PI + 清支付证据 */
    void resubmitTax(Long id, FinanceTaxPaymentCreateAndStartReqVO reqVO, Long userId);

    void resubmit(Long id, FinancePaymentApplicationResubmitReqVO reqVO, Long userId);

    /**
     * 申请人撤回：取消 BPM 实例并同步台账 CANCELLED（PAY-R2，对齐合同 cancel）。
     */
    void cancel(Long id, Long userId);

    /**
     * PAY-R8：FA/运维重放 REJECTED/CANCELLED（禁止 PAID），须 processInstanceId 与台账一致。
     */
    void replayTerminalOutcome(Long id, String outcome, String processInstanceId);

    FinancePaymentApplicationDO getApplication(Long id);

    /**
     * 详情可读：本人 / FA manageAll / 当前流程 active 任务候选人办理人（F3）。
     */
    FinancePaymentApplicationDO getApplicationForRead(Long id, Long userId, boolean manageAll);

    /** 普通付款读：强制 application_kind=ORDINARY */
    FinancePaymentApplicationDO getOrdinaryApplicationForRead(Long id, Long userId, boolean manageAll);

    boolean canAccessDetail(Long id, Long userId);

    PageResult<FinancePaymentApplicationDO> getApplicationPage(FinancePaymentApplicationPageReqVO pageReqVO,
                                                               Long loginUserId, boolean manageAll);

    BigDecimal sumPaidByPayee(Long payeeCompanyId);

    void updateCurrentNode(Long appId, String nodeKey, String nodeName);

    /**
     * 流程终态：APPROVED→WAIT_PAY（已通过待支付），REJECTED，CANCELLED（幂等）
     * <p>PAID 仅由出纳登记支付后写入，且必须已有 actual_pay_date + pay_voucher_url（F1）。
     */
    void onApprovalOutcome(Long appId, String outcome, String processInstanceId);

    void recordPay(FinancePaymentRecordPayReqVO reqVO, Long userId);

    /** 出纳确认补票完成并 complete 出纳任务（已付款） */
    void confirmMaterials(Long id, String taskId, Long userId);

    List<FinancePaymentPayLineRespVO> listPayLines(Long paymentApplicationId);

    List<FinancePaymentSalaryLineRespVO> listSalaryLines(Long paymentApplicationId);

    List<FinancePaymentTaxLineRespVO> listTaxLines(Long paymentApplicationId);

    BigDecimal sumPayLines(Long paymentApplicationId);

    /**
     * 出纳 task complete 守卫：台账须已写支付日+凭证（F1，对齐合同 assertExecutionEvidence）。
     */
    void assertCashierEvidenceForComplete(Long appId);

    /**
     * 财务节点 complete 兼容入口：普通付款不要求科目，薪资与税款保留科目非空校验。
     */
    void assertFinanceSubjectForComplete(Long appId);

    /**
     * 财务节点写入会计科目（办理人/候选人）。
     */
    void updateAccountingSubject(Long id, String accountingSubject, String taskId, Long userId);

}
