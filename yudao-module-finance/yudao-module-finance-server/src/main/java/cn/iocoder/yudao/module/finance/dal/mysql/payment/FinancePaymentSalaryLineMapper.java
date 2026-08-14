package cn.iocoder.yudao.module.finance.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentSalaryLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinancePaymentSalaryLineMapper extends BaseMapperX<FinancePaymentSalaryLineDO> {

    default List<FinancePaymentSalaryLineDO> selectByApplicationId(Long paymentApplicationId) {
        return selectList(new LambdaQueryWrapperX<FinancePaymentSalaryLineDO>()
                .eq(FinancePaymentSalaryLineDO::getPaymentApplicationId, paymentApplicationId)
                .orderByAsc(FinancePaymentSalaryLineDO::getSort)
                .orderByAsc(FinancePaymentSalaryLineDO::getId));
    }

    default void deleteByApplicationId(Long paymentApplicationId) {
        delete(new LambdaQueryWrapperX<FinancePaymentSalaryLineDO>()
                .eq(FinancePaymentSalaryLineDO::getPaymentApplicationId, paymentApplicationId));
    }

}
