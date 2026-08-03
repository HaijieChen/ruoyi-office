package cn.iocoder.yudao.module.finance.dal.mysql.receipt;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.receipt.vo.FinanceReceiptPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.receipt.FinanceReceiptDO;
import cn.iocoder.yudao.module.finance.enums.FinanceReceiptClaimStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper
public interface FinanceBankReceiptMapper extends BaseMapperX<FinanceReceiptDO> {

    default PageResult<FinanceReceiptDO> selectUnclaimedPage(FinanceReceiptPageReqVO reqVO) {
        return selectPage(reqVO, new MPJLambdaWrapperX<FinanceReceiptDO>()
                .in(FinanceReceiptDO::getClaimStatus, List.of(FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus(),
                        FinanceReceiptClaimStatusEnum.PARTIALLY_CLAIMED.getStatus()))
                .gtIfPresent(FinanceReceiptDO::getUnclaimedAmount, BigDecimal.ZERO)
                .likeIfPresent(FinanceReceiptDO::getReceiptNo, reqVO.getReceiptNo())
                .likeIfPresent(FinanceReceiptDO::getBankAccount, reqVO.getBankAccount())
                .eqIfPresent(FinanceReceiptDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .betweenIfPresent(FinanceReceiptDO::getTransactionDate, reqVO.getTransactionDate())
                .likeIfPresent(FinanceReceiptDO::getPayerName, reqVO.getPayerName())
                .likeIfPresent(FinanceReceiptDO::getPayerAccount, reqVO.getPayerAccount())
                .likeIfPresent(FinanceReceiptDO::getBankSerialNo, reqVO.getBankSerialNo())
                .eqIfPresent(FinanceReceiptDO::getBusinessFund, reqVO.getBusinessFund())
                .betweenIfPresent(FinanceReceiptDO::getImportDate, reqVO.getImportDate())
                .orderByDesc(FinanceReceiptDO::getId));
    }

    default PageResult<FinanceReceiptDO> selectReceiptPage(FinanceReceiptPageReqVO reqVO) {
        return selectPage(reqVO, new MPJLambdaWrapperX<FinanceReceiptDO>()
                .eqIfPresent(FinanceReceiptDO::getClaimStatus, reqVO.getClaimStatus())
                .likeIfPresent(FinanceReceiptDO::getReceiptNo, reqVO.getReceiptNo())
                .likeIfPresent(FinanceReceiptDO::getBankAccount, reqVO.getBankAccount())
                .eqIfPresent(FinanceReceiptDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .betweenIfPresent(FinanceReceiptDO::getTransactionDate, reqVO.getTransactionDate())
                .likeIfPresent(FinanceReceiptDO::getPayerName, reqVO.getPayerName())
                .likeIfPresent(FinanceReceiptDO::getPayerAccount, reqVO.getPayerAccount())
                .likeIfPresent(FinanceReceiptDO::getBankSerialNo, reqVO.getBankSerialNo())
                .eqIfPresent(FinanceReceiptDO::getBusinessFund, reqVO.getBusinessFund())
                .betweenIfPresent(FinanceReceiptDO::getImportDate, reqVO.getImportDate())
                .orderByDesc(FinanceReceiptDO::getId));
    }

    default FinanceReceiptDO selectByReceiptNo(String receiptNo) {
        return selectOne("receipt_no", receiptNo);
    }

    default FinanceReceiptDO selectByBankSerialNo(String bankSerialNo) {
        return selectOne("bank_serial_no", bankSerialNo);
    }

    default List<FinanceReceiptDO> selectListByIds(Collection<Long> ids) {
        return selectList(new MPJLambdaWrapperX<FinanceReceiptDO>().in(FinanceReceiptDO::getId, ids));
    }

    /**
     * 认领金额增加。
     * <p>
     * 注意：MySQL 单表 UPDATE 的 SET 从左到右赋值，后写字段可能读到前面字段的新值。
     * 因此 claim_status 必须先于 claimed/unclaimed 金额更新，否则
     * {@code CASE WHEN unclaimed_amount - amount = 0} 会在 unclaimed 已减为 0 后变成
     * {@code 0 - amount != 0}，错误落成「部分认领」。
     */
    @Update("UPDATE finance_bank_receipt SET " +
            "claim_status = CASE WHEN unclaimed_amount = #{amount} THEN 2 ELSE 1 END, " +
            "claimed_amount = claimed_amount + #{amount}, " +
            "unclaimed_amount = unclaimed_amount - #{amount}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND claim_status IN (0, 1) AND unclaimed_amount >= #{amount} AND deleted = b'0'")
    int increaseClaimedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 认领金额回退（撤销）。claim_status 同样必须先于金额字段更新，理由同 {@link #increaseClaimedAmount}。
     */
    @Update("UPDATE finance_bank_receipt SET " +
            "claim_status = CASE WHEN claimed_amount = #{amount} THEN 0 ELSE 1 END, " +
            "claimed_amount = claimed_amount - #{amount}, " +
            "unclaimed_amount = unclaimed_amount + #{amount}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND claim_status IN (1, 2) AND claimed_amount >= #{amount} AND deleted = b'0'")
    int decreaseClaimedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE finance_bank_receipt SET claim_status = 3, update_time = NOW() " +
            "WHERE id = #{id} AND claim_status = #{expectedStatus} AND claim_status IN (0, 1) " +
            "AND unclaimed_amount > 0 AND deleted = b'0'")
    int closeIfStatus(@Param("id") Long id, @Param("expectedStatus") Integer expectedStatus);

    @Update("UPDATE finance_bank_receipt " +
            "SET claim_status = CASE WHEN claimed_amount = 0 THEN 0 ELSE 1 END, update_time = NOW() " +
            "WHERE id = #{id} AND claim_status = 3 AND unclaimed_amount > 0 AND deleted = b'0'")
    int reopenIfClosed(@Param("id") Long id);

    /**
     * 待确认占用增加：pending + amount &lt;= unclaimed（unclaimed=交易额-已确认认领）。
     */
    @Update("UPDATE finance_bank_receipt SET " +
            "pending_claimed_amount = IFNULL(pending_claimed_amount, 0) + #{amount}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND claim_status IN (0, 1) AND deleted = b'0' " +
            "AND IFNULL(pending_claimed_amount, 0) + #{amount} <= unclaimed_amount")
    int increasePendingClaimedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 待确认占用释放（驳回 / 确认前 pending→claimed）。
     */
    @Update("UPDATE finance_bank_receipt SET " +
            "pending_claimed_amount = IFNULL(pending_claimed_amount, 0) - #{amount}, " +
            "update_time = NOW() " +
            "WHERE id = #{id} AND deleted = b'0' " +
            "AND IFNULL(pending_claimed_amount, 0) >= #{amount}")
    int decreasePendingClaimedAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

}
