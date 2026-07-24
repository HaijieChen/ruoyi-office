package cn.iocoder.yudao.module.finance.dal.mysql.claim;

import cn.iocoder.yudao.module.finance.dal.dataobject.claim.FinanceReceiptClaimRevokeAuditDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FinanceReceiptClaimRevokeAuditMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("INSERT INTO finance_receipt_claim_revoke_audit (claim_id, reviewer_id, revoke_time, revoke_reason) " +
            "VALUES (#{claimId}, #{reviewerId}, #{revokeTime}, #{revokeReason})")
    int insert(FinanceReceiptClaimRevokeAuditDO audit);

    @Select("SELECT id, claim_id, reviewer_id, revoke_time, revoke_reason " +
            "FROM finance_receipt_claim_revoke_audit " +
            "WHERE claim_id = #{claimId} AND deleted = b'0' " +
            "ORDER BY revoke_time DESC, id DESC")
    @Results(id = "revokeAuditResultMap", value = {
            @Result(property = "id", column = "id", id = true),
            @Result(property = "claimId", column = "claim_id"),
            @Result(property = "reviewerId", column = "reviewer_id"),
            @Result(property = "revokeTime", column = "revoke_time"),
            @Result(property = "revokeReason", column = "revoke_reason")
    })
    List<FinanceReceiptClaimRevokeAuditDO> selectListByClaimId(Long claimId);

}
