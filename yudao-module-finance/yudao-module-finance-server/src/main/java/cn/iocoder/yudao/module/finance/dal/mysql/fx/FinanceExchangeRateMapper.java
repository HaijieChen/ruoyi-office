package cn.iocoder.yudao.module.finance.dal.mysql.fx;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.fx.vo.FinanceExchangeRatePageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.fx.FinanceExchangeRateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinanceExchangeRateMapper extends BaseMapperX<FinanceExchangeRateDO> {
    default PageResult<FinanceExchangeRateDO> selectPage(FinanceExchangeRatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceExchangeRateDO>()
                .eqIfPresent(FinanceExchangeRateDO::getPeriodLabel, reqVO.getPeriodLabel())
                .eqIfPresent(FinanceExchangeRateDO::getFromCurrency, reqVO.getFromCurrency())
                .eqIfPresent(FinanceExchangeRateDO::getToCurrency, reqVO.getToCurrency())
                .orderByDesc(FinanceExchangeRateDO::getPeriodLabel)
                .orderByDesc(FinanceExchangeRateDO::getId));
    }

    default FinanceExchangeRateDO selectPair(String periodLabel, String fromCurrency, String toCurrency) {
        return selectOne(new LambdaQueryWrapperX<FinanceExchangeRateDO>()
                .eq(FinanceExchangeRateDO::getPeriodLabel, periodLabel)
                .eq(FinanceExchangeRateDO::getFromCurrency, fromCurrency)
                .eq(FinanceExchangeRateDO::getToCurrency, toCurrency));
    }
}
