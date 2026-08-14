package cn.iocoder.yudao.module.finance.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentPayLineDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface FinancePaymentPayLineMapper extends BaseMapperX<FinancePaymentPayLineDO> {

    default List<FinancePaymentPayLineDO> selectByApplicationId(Long paymentApplicationId) {
        return selectList(new LambdaQueryWrapperX<FinancePaymentPayLineDO>()
                .eq(FinancePaymentPayLineDO::getPaymentApplicationId, paymentApplicationId)
                .orderByAsc(FinancePaymentPayLineDO::getId));
    }

    default FinancePaymentPayLineDO selectByAppAndIdempotencyKey(Long paymentApplicationId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<FinancePaymentPayLineDO>()
                .eq(FinancePaymentPayLineDO::getPaymentApplicationId, paymentApplicationId)
                .eq(FinancePaymentPayLineDO::getIdempotencyKey, idempotencyKey));
    }

    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM finance_payment_pay_line "
            + "WHERE payment_application_id = #{paymentApplicationId} AND deleted = b'0'")
    BigDecimal sumPayAmountByApplicationId(Long paymentApplicationId);

}
