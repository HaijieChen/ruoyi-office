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
     * 内部终态落账（仅 BPM Delegate / StatusListener）。不暴露用户 HTTP。
     *
     * @param processInstanceId 触发终态的实际流程实例；与台账当前 process 不一致时幂等忽略（防旧实例污染重提）
     */
    void onApprovalOutcome(Long appId, String outcome, String processInstanceId);

    FinanceContractApplicationDO getApplication(Long id);

    /**
     * 详情：BS 仅本人；manageAll=true（FA）可看全部。
     */
    FinanceContractApplicationDO getApplication(Long id, Long userId, boolean manageAll);

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

    /** 归档：写台账 + complete */
    void recordArchive(Long id, String taskId, Long userId);

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
}
