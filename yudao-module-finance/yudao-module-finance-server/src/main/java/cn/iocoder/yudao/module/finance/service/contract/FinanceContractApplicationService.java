package cn.iocoder.yudao.module.finance.service.contract;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationCreateAndStartReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationResubmitReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;

public interface FinanceContractApplicationService {

    Long createAndStart(FinanceContractApplicationCreateAndStartReqVO reqVO, Long applicantUserId);

    void resubmit(Long id, FinanceContractApplicationResubmitReqVO reqVO, Long userId);

    /**
     * 申请人撤回（仅用印前）；同步取消 Flowable，并落 CANCELLED。
     */
    void cancel(Long id, Long userId);

    /**
     * 逻辑删除整条合同签约单据。审批中须走撤回；已被商务单或付款引用则拒绝。
     */
    void delete(Long id);

    void update(Long id, FinanceContractApplicationCreateAndStartReqVO reqVO);

    cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO deleteList(
            java.util.List<Long> ids);

    cn.iocoder.yudao.module.finance.controller.admin.common.vo.FinanceBatchDeleteRespVO deleteByQuery(
            FinanceContractApplicationPageReqVO reqVO);

    java.util.List<FinanceContractApplicationDO> listForExport(FinanceContractApplicationPageReqVO reqVO);

    /**
     * 内部终态落账（仅 BPM Delegate / StatusListener）。不暴露用户 HTTP。
     *
     * @param processInstanceId 触发终态的实际流程实例；与台账当前 process 不一致时幂等忽略（防旧实例污染重提）
     */
    void onApprovalOutcome(Long appId, String outcome, String processInstanceId);

    FinanceContractApplicationDO getApplication(Long id);

    /**
     * 详情：BS 仅本人；manageAll=true（FA）可看全部；
     * 当前用户为绑定 process 上 active 任务候选人/办理人时可读本笔（C29 任务语境）。
     */
    FinanceContractApplicationDO getApplication(Long id, Long userId, boolean manageAll);

    /**
     * 详情读权探针（C30 / CS-R5）：本人或 process 上 active 任务候选人/办理人。
     * 不含 manageAll；供 HTTP 层在无静态 query 时放行进入 service。
     * 申请不存在时返回 false（不抛业务异常）。
     */
    boolean canAccessDetail(Long id, Long userId);

    PageResult<FinanceContractApplicationDO> getApplicationPage(FinanceContractApplicationPageReqVO pageReqVO);

    /**
     * 分页：BS 强制 applicant=me；manageAll 可全量。
     */
    PageResult<FinanceContractApplicationDO> getApplicationPage(FinanceContractApplicationPageReqVO pageReqVO,
                                                                Long userId, boolean manageAll);

    void updateCurrentNode(Long appId, String nodeKey, String nodeName);

    /**
     * 用印：写台账 + 校验 task + complete（CS-F2）。
     */
    void recordSeal(Long id, String taskId, String sealFileUrl, Long userId);

    /** 归档：列表操作，只写 archived_at，不 complete BPM 任务 */
    void recordArchive(Long id, Long userId);

    /** 列表归档：必填多份资料，只写台账，不 complete BPM */
    void recordArchive(Long id, java.util.List<String> archiveFileUrls, Long userId);

    /** 邮寄：写台账 + complete */
    void recordMail(Long id, String taskId, String mailTrackingNo, Long userId);

    /**
     * BPM complete 守卫与 end 复核共用：校验执行证据。
     */
    void assertExecutionEvidenceForComplete(Long appId, String taskDefinitionKey);

    /**
     * 从台账权威回写 needMail 流程变量（防任务 variables 篡改）。
     */
    boolean resolveNeedMailFromLedger(Long appId);

    /** BO 选择器：已通过且申请人=me */
    java.util.List<FinanceContractApplicationDO> listSelectableForBo(Long applicantUserId);

    /** 业务付款前置：已通过且 fileType=付款业务合同 */
    java.util.List<FinanceContractApplicationDO> listSelectableForBusinessPayment(Long applicantUserId);
}
