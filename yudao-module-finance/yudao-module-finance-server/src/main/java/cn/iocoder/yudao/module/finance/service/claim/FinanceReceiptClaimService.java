package cn.iocoder.yudao.module.finance.service.claim;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimPageReqVO;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimSaveReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimRevokeAuditDO;
import jakarta.validation.Valid;

import java.util.List;

public interface FinanceReceiptClaimService {

    Long createClaim(@Valid FinanceReceiptClaimSaveReqVO createReqVO, Long claimantId);

    void updateClaim(@Valid FinanceReceiptClaimSaveReqVO updateReqVO, Long claimantId);

    void confirmClaim(Long id, Long reviewerId);

    void rejectClaim(Long id, Long reviewerId, String reason);

    void revokeClaim(Long id, Long reviewerId, String reason);

    void resubmitClaim(Long id, Long claimantId);

    PageResult<FinanceReceiptClaimDO> getMyClaimPage(FinanceReceiptClaimPageReqVO pageReqVO, Long claimantId);

    PageResult<FinanceReceiptClaimDO> getReviewClaimPage(FinanceReceiptClaimPageReqVO pageReqVO);

    FinanceReceiptClaimDetail getClaimDetail(Long id, Long viewerId, boolean reviewer);

    List<FinanceReceiptClaimRevokeAuditDO> getRevokeAuditList(Long claimId);

}
