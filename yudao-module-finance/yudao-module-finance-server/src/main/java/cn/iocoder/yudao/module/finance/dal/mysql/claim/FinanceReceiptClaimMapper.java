package cn.iocoder.yudao.module.finance.dal.mysql.claim;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.claim.vo.FinanceReceiptClaimPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FinanceReceiptClaimMapper extends BaseMapperX<FinanceReceiptClaimDO> {

    default PageResult<FinanceReceiptClaimDO> selectClaimPage(FinanceReceiptClaimPageReqVO reqVO,
                                                              Long claimantId) {
        return selectPage(reqVO, new MPJLambdaWrapperX<FinanceReceiptClaimDO>()
                .eqIfPresent(FinanceReceiptClaimDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FinanceReceiptClaimDO::getClaimantId,
                        claimantId == null ? reqVO.getClaimantId() : claimantId)
                .betweenIfPresent(FinanceReceiptClaimDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(FinanceReceiptClaimDO::getId));
    }

    @Update("UPDATE finance_receipt_claim SET total_claim_amount = #{claim.totalClaimAmount}, " +
            "remark = #{claim.remark}, update_time = NOW() WHERE id = #{claim.id} " +
            "AND claimant_id = #{claimantId} AND status = #{expectedStatus} AND deleted = b'0'")
    int updateEditableClaim(@Param("claim") FinanceReceiptClaimDO claim,
                            @Param("claimantId") Long claimantId,
                            @Param("expectedStatus") Integer expectedStatus);

    @Update("UPDATE finance_receipt_claim SET status = #{claim.status}, reviewer_id = #{claim.reviewerId}, " +
            "review_time = #{claim.reviewTime}, reject_reason = #{claim.rejectReason}, " +
            "revoke_reason = #{claim.revokeReason}, update_time = NOW() " +
            "WHERE id = #{claim.id} AND status = #{expectedStatus} AND deleted = b'0'")
    int updateStatusIfMatch(@Param("claim") FinanceReceiptClaimDO claim,
                            @Param("expectedStatus") Integer expectedStatus);

    @Update("UPDATE finance_receipt_claim SET status = 0, reviewer_id = NULL, review_time = NULL, " +
            "reject_reason = NULL, update_time = NOW() WHERE id = #{id} AND claimant_id = #{claimantId} " +
            "AND status = 2 AND deleted = b'0'")
    int updateRejectedToPending(@Param("id") Long id, @Param("claimantId") Long claimantId);

}
