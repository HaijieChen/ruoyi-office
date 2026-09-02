package cn.iocoder.yudao.module.finance.dal.mysql.invoice;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.invoice.vo.FinanceInvoiceApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 开票申请台账 Mapper
 */
@Mapper
public interface FinanceInvoiceApplicationMapper extends BaseMapperX<FinanceInvoiceApplicationDO> {

    default LambdaQueryWrapperX<FinanceInvoiceApplicationDO> buildQuery(FinanceInvoiceApplicationPageReqVO reqVO) {
        return new LambdaQueryWrapperX<FinanceInvoiceApplicationDO>()
                .likeIfPresent(FinanceInvoiceApplicationDO::getApplicationNo, reqVO.getApplicationNo())
                .eqIfPresent(FinanceInvoiceApplicationDO::getApprovalStatus, reqVO.getApprovalStatus())
                .eqIfPresent(FinanceInvoiceApplicationDO::getIssueStatus, reqVO.getIssueStatus())
                .eqIfPresent(FinanceInvoiceApplicationDO::getApplicantUserId, reqVO.getApplicantUserId())
                .likeIfPresent(FinanceInvoiceApplicationDO::getBuyerName, reqVO.getBuyerName())
                .orderByDesc(FinanceInvoiceApplicationDO::getId);
    }

    default PageResult<FinanceInvoiceApplicationDO> selectPage(FinanceInvoiceApplicationPageReqVO reqVO) {
        return selectPage(reqVO, buildQuery(reqVO));
    }

    default List<FinanceInvoiceApplicationDO> selectListByQuery(FinanceInvoiceApplicationPageReqVO reqVO) {
        return selectList(buildQuery(reqVO));
    }

    /** 到款认领可选源：申请人本人或明细商务单导入人。 */
    default PageResult<FinanceInvoiceApplicationDO> selectClaimableSourcePage(
            FinanceInvoiceApplicationPageReqVO reqVO, Long userId) {
        String importerSql = "SELECT l.application_id FROM finance_invoice_application_line l "
                + "INNER JOIN finance_business_order bo ON bo.id = l.business_order_id "
                + "WHERE l.deleted = b'0' AND bo.deleted = b'0' AND bo.importer_id = " + userId;
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceInvoiceApplicationDO>()
                .likeIfPresent(FinanceInvoiceApplicationDO::getApplicationNo, reqVO.getApplicationNo())
                .eqIfPresent(FinanceInvoiceApplicationDO::getApprovalStatus, reqVO.getApprovalStatus())
                .eqIfPresent(FinanceInvoiceApplicationDO::getIssueStatus, reqVO.getIssueStatus())
                .likeIfPresent(FinanceInvoiceApplicationDO::getBuyerName, reqVO.getBuyerName())
                .and(w -> w.eq(FinanceInvoiceApplicationDO::getApplicantUserId, userId)
                        .or()
                        .inSql(FinanceInvoiceApplicationDO::getId, importerSql))
                .orderByDesc(FinanceInvoiceApplicationDO::getId));
    }

    default FinanceInvoiceApplicationDO selectByApplicationNo(String applicationNo) {
        return selectOne(new LambdaQueryWrapperX<FinanceInvoiceApplicationDO>()
                .eq(FinanceInvoiceApplicationDO::getApplicationNo, applicationNo));
    }

    default List<FinanceInvoiceApplicationDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<FinanceInvoiceApplicationDO>()
                .in(FinanceInvoiceApplicationDO::getId, ids));
    }

    /**
     * 待确认认领占用：pending + confirmed + amount &lt;= total；仅 APPROVED 且未作废。
     */
    @Update("UPDATE finance_invoice_application SET " +
            "pending_claimed_amount = IFNULL(pending_claimed_amount, 0) + #{amount}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND approval_status = 'APPROVED' AND (voided = b'0' OR voided IS NULL) " +
            "AND deleted = b'0' " +
            "AND (red_flushed = b'0' OR red_flushed IS NULL) " +
            "AND red_flush_lock_application_id IS NULL " +
            "AND IFNULL(pending_claimed_amount, 0) + IFNULL(confirmed_claimed_amount, 0) + #{amount} <= total_amount")
    int increasePendingClaimedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE finance_invoice_application SET " +
            "pending_claimed_amount = IFNULL(pending_claimed_amount, 0) - #{amount}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND IFNULL(pending_claimed_amount, 0) >= #{amount}")
    int decreasePendingClaimedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 确认：pending→confirmed
     */
    @Update("UPDATE finance_invoice_application SET " +
            "pending_claimed_amount = IFNULL(pending_claimed_amount, 0) - #{amount}, " +
            "confirmed_claimed_amount = IFNULL(confirmed_claimed_amount, 0) + #{amount}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND IFNULL(pending_claimed_amount, 0) >= #{amount}")
    int confirmPendingToClaimed(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 撤销已确认：confirmed 回退（不产生 pending）
     */
    @Update("UPDATE finance_invoice_application SET " +
            "confirmed_claimed_amount = IFNULL(confirmed_claimed_amount, 0) - #{amount}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND IFNULL(confirmed_claimed_amount, 0) >= #{amount}")
    int decreaseConfirmedClaimedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 红冲可选原单：已通过、办票完成、未作废、未红冲、未锁定、无认领。
     */
    default List<FinanceInvoiceApplicationDO> selectSelectableForRedFlush() {
        return selectList(new LambdaQueryWrapperX<FinanceInvoiceApplicationDO>()
                .eq(FinanceInvoiceApplicationDO::getApprovalStatus, "APPROVED")
                .eq(FinanceInvoiceApplicationDO::getIssueStatus, 2)
                .and(w -> w.eq(FinanceInvoiceApplicationDO::getVoided, false)
                        .or()
                        .isNull(FinanceInvoiceApplicationDO::getVoided))
                .and(w -> w.eq(FinanceInvoiceApplicationDO::getRedFlushed, false)
                        .or()
                        .isNull(FinanceInvoiceApplicationDO::getRedFlushed))
                .isNull(FinanceInvoiceApplicationDO::getRedFlushLockApplicationId)
                .and(w -> w.apply("IFNULL(pending_claimed_amount,0) = 0")
                        .apply("IFNULL(confirmed_claimed_amount,0) = 0"))
                .orderByDesc(FinanceInvoiceApplicationDO::getId));
    }

    @Update("UPDATE finance_invoice_application SET " +
            "red_flush_lock_application_id = #{lockId}, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND approval_status = 'APPROVED' AND issue_status = 2 " +
            "AND (voided = b'0' OR voided IS NULL) " +
            "AND (red_flushed = b'0' OR red_flushed IS NULL) " +
            "AND red_flush_lock_application_id IS NULL " +
            "AND IFNULL(pending_claimed_amount, 0) = 0 " +
            "AND IFNULL(confirmed_claimed_amount, 0) = 0")
    int tryLockForRedFlush(@Param("id") Long id, @Param("lockId") Long lockId);

    @Update("UPDATE finance_invoice_application SET " +
            "red_flush_lock_application_id = NULL, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND red_flush_lock_application_id = #{lockId}")
    int unlockRedFlush(@Param("id") Long id, @Param("lockId") Long lockId);

    @Update("UPDATE finance_invoice_application SET " +
            "red_flushed = b'1', red_flush_lock_application_id = NULL, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0'")
    int markRedFlushed(@Param("id") Long id);

}
