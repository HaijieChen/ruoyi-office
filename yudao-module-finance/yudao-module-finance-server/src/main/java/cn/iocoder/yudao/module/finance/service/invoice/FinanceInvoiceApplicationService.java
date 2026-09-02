package cn.iocoder.yudao.module.finance.service.invoice;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCompleteIssueReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationUpdateIssueProgressReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationFileDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;

/**
 * 开票申请 Service。
 * <p>商务单 {@code invoiced_occupied_amount} 由 createAndStart / resubmit / onApprovalOutcome 释占，以及红冲办票 {@link #releaseOccupyForRedFlush} 写。
 * <p>{@code issue_status}：主路径 {@link #completeIssue} 写 FULL；兼容路径 {@link #updateIssueProgress} 保留。
 */
public interface FinanceInvoiceApplicationService {

    /**
     * 创建开票申请并启动审批流程（无草稿）。
     *
     * @return 申请主键
     */
    Long createAndStart(@Valid FinanceInvoiceApplicationCreateAndStartReqVO reqVO, Long applicantUserId);

    /**
     * 同步审批落账（主路径由 Flowable 同步 Delegate 调用；同 outcome 幂等）。
     *
     * @param appId   申请主键
     * @param outcome APPROVED | REJECTED | CANCELLED（与 {@code FinanceInvoiceApprovalStatusEnum} 对齐）
     */
    void onApprovalOutcome(Long appId, String outcome);

    /**
     * 驳回后重提：释占 → 换明细 → 再占 → 新 processInstance；不删历史流程。
     */
    void resubmit(Long appId, @Valid FinanceInvoiceApplicationResubmitReqVO reqVO, Long userId);

    /**
     * 整单办票（I2）：replace 附件子表，写 app.issue_status=FULL(2)。
     */
    void completeIssue(@Valid FinanceInvoiceApplicationCompleteIssueReqVO reqVO);

    /**
     * 一行一票办票（兼容；新流程请用 {@link #completeIssue}）。
     */
    @Deprecated
    void updateIssueProgress(@Valid FinanceInvoiceApplicationUpdateIssueProgressReqVO reqVO);

    FinanceInvoiceApplicationDO getApplication(Long id);

    List<FinanceInvoiceApplicationLineDO> getApplicationLines(Long applicationId);

    List<FinanceInvoiceApplicationFileDO> getApplicationFiles(Long applicationId);

    PageResult<FinanceInvoiceApplicationDO> getApplicationPage(FinanceInvoiceApplicationPageReqVO pageReqVO);

    PageResult<FinanceInvoiceApplicationDO> getClaimableSourcePage(
            FinanceInvoiceApplicationPageReqVO pageReqVO, Long userId);

    /**
     * 已结束的流程实例 id。空 id 不入结果；历史导入无流程由调用方视为已结束。
     */
    java.util.Set<String> listEndedProcessInstanceIds(java.util.Collection<String> processInstanceIds);

    /**
     * 红冲可选前置开票申请（R1）。
     */
    List<FinanceInvoiceApplicationDO> listSelectableForRedFlush();

    /**
     * 红冲办票：按原单明细释放商务单占用，标记已红冲并清锁。已红冲则幂等。
     */
    void releaseOccupyForRedFlush(Long predecessorApplicationId);

    /**
     * 合同已占用开票金额：审批中 + 已通过且未作废。
     */
    BigDecimal occupiedInvoiceAmount(Long contractId, Long excludeApplicationId);

}
