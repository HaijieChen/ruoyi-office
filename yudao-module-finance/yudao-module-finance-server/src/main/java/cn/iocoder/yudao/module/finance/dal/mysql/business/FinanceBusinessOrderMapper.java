package cn.iocoder.yudao.module.finance.dal.mysql.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper
public interface FinanceBusinessOrderMapper extends BaseMapperX<FinanceBusinessOrderDO> {

    default PageResult<FinanceBusinessOrderDO> selectPage(FinanceBusinessOrderPageReqVO reqVO) {
        MPJLambdaWrapperX<FinanceBusinessOrderDO> wrapper = new MPJLambdaWrapperX<FinanceBusinessOrderDO>()
                .likeIfPresent(FinanceBusinessOrderDO::getOrderNo, reqVO.getOrderNo())
                .likeIfPresent(FinanceBusinessOrderDO::getBankAccount, reqVO.getBankAccount())
                .likeIfPresent(FinanceBusinessOrderDO::getContractProcessId, reqVO.getContractProcessId())
                .likeIfPresent(FinanceBusinessOrderDO::getProductName, reqVO.getProductName())
                .likeIfPresent(FinanceBusinessOrderDO::getPayerName, reqVO.getPayerName())
                .eqIfPresent(FinanceBusinessOrderDO::getImporterId, reqVO.getImporterId())
                .betweenIfPresent(FinanceBusinessOrderDO::getImportDate, reqVO.getImportDate())
                .betweenIfPresent(FinanceBusinessOrderDO::getOrderDate, reqVO.getOrderDate());
        // 可开余额 > 0：settlement_amount - IFNULL(invoiced_occupied_amount,0) > 0
        if (Boolean.TRUE.equals(reqVO.getOnlyOpenable())) {
            wrapper.apply("settlement_amount > IFNULL(invoiced_occupied_amount, 0)");
        }
        return selectPage(reqVO, wrapper.orderByDesc(FinanceBusinessOrderDO::getId));
    }

    default PageResult<FinanceBusinessOrderDO> selectClaimablePage(FinanceBusinessOrderPageReqVO reqVO,
                                                                   Long importerId) {
        return selectPage(reqVO, new MPJLambdaWrapperX<FinanceBusinessOrderDO>()
                .likeIfPresent(FinanceBusinessOrderDO::getOrderNo, reqVO.getOrderNo())
                .likeIfPresent(FinanceBusinessOrderDO::getBankAccount, reqVO.getBankAccount())
                .likeIfPresent(FinanceBusinessOrderDO::getContractProcessId, reqVO.getContractProcessId())
                .likeIfPresent(FinanceBusinessOrderDO::getProductName, reqVO.getProductName())
                .likeIfPresent(FinanceBusinessOrderDO::getPayerName, reqVO.getPayerName())
                .eq(FinanceBusinessOrderDO::getImporterId, importerId)
                .betweenIfPresent(FinanceBusinessOrderDO::getImportDate, reqVO.getImportDate())
                .betweenIfPresent(FinanceBusinessOrderDO::getOrderDate, reqVO.getOrderDate())
                .apply("settlement_amount > confirmed_claimed_amount")
                .orderByDesc(FinanceBusinessOrderDO::getId));
    }

    default FinanceBusinessOrderDO selectByOrderNo(String orderNo) {
        return selectOne("order_no", orderNo);
    }

    default FinanceBusinessOrderDO selectBySourceRowHash(String sourceRowHash) {
        return selectOne("source_row_hash", sourceRowHash);
    }

    default List<FinanceBusinessOrderDO> selectListByIds(Collection<Long> ids) {
        return selectList(new MPJLambdaWrapperX<FinanceBusinessOrderDO>().in(FinanceBusinessOrderDO::getId, ids));
    }

    @Update("UPDATE finance_business_order SET confirmed_claimed_amount = confirmed_claimed_amount + #{amount}, " +
            "update_time = NOW() WHERE id = #{id} AND importer_id = #{importerId} " +
            "AND settlement_amount - confirmed_claimed_amount >= #{amount} AND deleted = b'0'")
    int increaseConfirmedClaimedAmount(@Param("id") Long id, @Param("importerId") Long importerId,
                                       @Param("amount") BigDecimal amount);

    @Update("UPDATE finance_business_order SET confirmed_claimed_amount = confirmed_claimed_amount - #{amount}, " +
            "update_time = NOW() WHERE id = #{id} AND importer_id = #{importerId} " +
            "AND confirmed_claimed_amount >= #{amount} AND deleted = b'0'")
    int decreaseConfirmedClaimedAmount(@Param("id") Long id, @Param("importerId") Long importerId,
                                       @Param("amount") BigDecimal amount);

    /**
     * CAS 增加开票占用：settlement_amount - invoiced_occupied_amount &gt;= amount。
     * <p>仅 createAndStart / resubmit（T3）写路径调用；其它路径禁止写占用。
     */
    @Update("UPDATE finance_business_order SET invoiced_occupied_amount = invoiced_occupied_amount + #{amount}, " +
            "update_time = NOW() WHERE id = #{id} " +
            "AND settlement_amount - invoiced_occupied_amount >= #{amount} AND deleted = b'0'")
    int increaseInvoicedOccupiedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * CAS 减少开票占用：invoiced_occupied_amount &gt;= amount。
     * <p>仅 onApprovalOutcome(REJECTED|CANCELLED) / resubmit 释占路径调用。
     */
    @Update("UPDATE finance_business_order SET invoiced_occupied_amount = invoiced_occupied_amount - #{amount}, " +
            "update_time = NOW() WHERE id = #{id} " +
            "AND invoiced_occupied_amount >= #{amount} AND deleted = b'0'")
    int decreaseInvoicedOccupiedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

}
