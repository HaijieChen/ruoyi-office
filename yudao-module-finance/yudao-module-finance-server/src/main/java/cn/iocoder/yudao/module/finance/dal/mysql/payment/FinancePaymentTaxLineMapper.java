package cn.iocoder.yudao.module.finance.dal.mysql.payment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentTaxLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinancePaymentTaxLineMapper extends BaseMapperX<FinancePaymentTaxLineDO> {

    default List<FinancePaymentTaxLineDO> selectByApplicationId(Long paymentApplicationId) {
        return selectList(new LambdaQueryWrapperX<FinancePaymentTaxLineDO>()
                .eq(FinancePaymentTaxLineDO::getPaymentApplicationId, paymentApplicationId)
                .orderByAsc(FinancePaymentTaxLineDO::getSort)
                .orderByAsc(FinancePaymentTaxLineDO::getId));
    }

    default void deleteByApplicationId(Long paymentApplicationId) {
        delete(new LambdaQueryWrapperX<FinancePaymentTaxLineDO>()
                .eq(FinancePaymentTaxLineDO::getPaymentApplicationId, paymentApplicationId));
    }

}
