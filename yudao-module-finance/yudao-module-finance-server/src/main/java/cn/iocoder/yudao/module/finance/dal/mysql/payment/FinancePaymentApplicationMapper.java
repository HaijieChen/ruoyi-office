package cn.iocoder.yudao.module.finance.dal.mysql.payment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.payment.vo.FinancePaymentApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.enums.FinancePaymentApplicationStatusEnum;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface FinancePaymentApplicationMapper extends BaseMapperX<FinancePaymentApplicationDO> {

    /**
     * EXP-87 F5：支付登记前对申请行加悲观锁，串行化 remaining 校验与明细插入。
     */
    @Select("SELECT * FROM finance_payment_application WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    FinancePaymentApplicationDO selectByIdForUpdate(@Param("id") Long id);

    default PageResult<FinancePaymentApplicationDO> selectPage(FinancePaymentApplicationPageReqVO reqVO,
                                                               Long applicantUserIdOrNull) {
        LambdaQueryWrapperX<FinancePaymentApplicationDO> wrapper = new LambdaQueryWrapperX<FinancePaymentApplicationDO>()
                .likeIfPresent(FinancePaymentApplicationDO::getApplicationNo, reqVO.getApplicationNo())
                .eqIfPresent(FinancePaymentApplicationDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FinancePaymentApplicationDO::getPaymentReason, reqVO.getPaymentReason())
                .eqIfPresent(FinancePaymentApplicationDO::getPayeeCompanyId, reqVO.getPayeeCompanyId())
                .likeIfPresent(FinancePaymentApplicationDO::getPayeeName, reqVO.getPayeeName())
                .eqIfPresent(FinancePaymentApplicationDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .eq(applicantUserIdOrNull != null, FinancePaymentApplicationDO::getApplicantUserId,
                        applicantUserIdOrNull)
                .orderByDesc(FinancePaymentApplicationDO::getId);
        // ORDINARY：兼容历史 application_kind 为空
        if ("ORDINARY".equals(reqVO.getApplicationKind())) {
            wrapper.and(w -> w.eq(FinancePaymentApplicationDO::getApplicationKind, "ORDINARY")
                    .or().isNull(FinancePaymentApplicationDO::getApplicationKind));
        } else {
            wrapper.eqIfPresent(FinancePaymentApplicationDO::getApplicationKind, reqVO.getApplicationKind());
        }
        return selectPage(reqVO, wrapper);
    }

    @Select("SELECT COALESCE(SUM(apply_amount), 0) FROM finance_payment_application "
            + "WHERE payee_company_id = #{payeeCompanyId} AND status = #{status} AND deleted = 0")
    BigDecimal sumAmountByPayeeAndStatus(@Param("payeeCompanyId") Long payeeCompanyId,
                                         @Param("status") String status);

    default BigDecimal sumPaidByPayee(Long payeeCompanyId) {
        BigDecimal sum = sumAmountByPayeeAndStatus(payeeCompanyId,
                FinancePaymentApplicationStatusEnum.PAID.getStatus());
        return sum != null ? sum : BigDecimal.ZERO;
    }

}
