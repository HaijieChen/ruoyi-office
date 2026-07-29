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

    default PageResult<FinanceInvoiceApplicationDO> selectPage(FinanceInvoiceApplicationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceInvoiceApplicationDO>()
                .likeIfPresent(FinanceInvoiceApplicationDO::getApplicationNo, reqVO.getApplicationNo())
                .eqIfPresent(FinanceInvoiceApplicationDO::getApprovalStatus, reqVO.getApprovalStatus())
                .eqIfPresent(FinanceInvoiceApplicationDO::getIssueStatus, reqVO.getIssueStatus())
                .eqIfPresent(FinanceInvoiceApplicationDO::getApplicantUserId, reqVO.getApplicantUserId())
                .likeIfPresent(FinanceInvoiceApplicationDO::getBuyerName, reqVO.getBuyerName())
                .orderByDesc(FinanceInvoiceApplicationDO::getId));
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

}
