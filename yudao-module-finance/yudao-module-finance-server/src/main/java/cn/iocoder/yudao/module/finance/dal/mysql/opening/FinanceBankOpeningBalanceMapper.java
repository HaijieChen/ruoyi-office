package cn.iocoder.yudao.module.finance.dal.mysql.opening;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.opening.vo.FinanceBankOpeningBalancePageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.opening.FinanceBankOpeningBalanceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinanceBankOpeningBalanceMapper extends BaseMapperX<FinanceBankOpeningBalanceDO> {

    default FinanceBankOpeningBalanceDO selectByAccountId(Long accountId) {
        return selectOne(FinanceBankOpeningBalanceDO::getAccountId, accountId);
    }

    default PageResult<FinanceBankOpeningBalanceDO> selectPage(FinanceBankOpeningBalancePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceBankOpeningBalanceDO>()
                .eqIfPresent(FinanceBankOpeningBalanceDO::getAccountId, reqVO.getAccountId())
                .eqIfPresent(FinanceBankOpeningBalanceDO::getCurrency, reqVO.getCurrency())
                .orderByDesc(FinanceBankOpeningBalanceDO::getId));
    }

}
