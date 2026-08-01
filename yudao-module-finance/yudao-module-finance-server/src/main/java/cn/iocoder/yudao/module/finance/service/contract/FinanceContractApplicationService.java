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
     * 申请人撤回（仅用印前）；映射 CANCELLED。
     */
    void cancel(Long id, Long userId);

    void onApprovalOutcome(Long appId, String outcome);

    FinanceContractApplicationDO getApplication(Long id);

    PageResult<FinanceContractApplicationDO> getApplicationPage(FinanceContractApplicationPageReqVO pageReqVO);

    /**
     * TaskListener 回写当前节点（CS-T3 接线）。
     */
    void updateCurrentNode(Long appId, String nodeKey, String nodeName);

    /** 用印节点：写 seal_file_url（CS-T4）；completeTask 由前端/BPM 编排 */
    void recordSeal(Long id, String sealFileUrl, Long actualSealerUserId);

    /** 归档节点 */
    void recordArchive(Long id);

    /** 邮寄节点 */
    void recordMail(Long id, String mailTrackingNo);

    /** BO 选择器：已通过且申请人=me */
    java.util.List<FinanceContractApplicationDO> listSelectableForBo(Long applicantUserId);
}
