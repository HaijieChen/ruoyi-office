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

    default PageResult<FinancePaymentApplicationDO> selectPage(FinancePaymentApplicationPageReqVO reqVO,
                                                               Long applicantUserIdOrNull) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinancePaymentApplicationDO>()
                .likeIfPresent(FinancePaymentApplicationDO::getApplicationNo, reqVO.getApplicationNo())
                .eqIfPresent(FinancePaymentApplicationDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FinancePaymentApplicationDO::getPaymentReason, reqVO.getPaymentReason())
                .eqIfPresent(FinancePaymentApplicationDO::getPayeeCompanyId, reqVO.getPayeeCompanyId())
                .likeIfPresent(FinancePaymentApplicationDO::getPayeeName, reqVO.getPayeeName())
                .eq(applicantUserIdOrNull != null, FinancePaymentApplicationDO::getApplicantUserId,
                        applicantUserIdOrNull)
                .orderByDesc(FinancePaymentApplicationDO::getId));
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
