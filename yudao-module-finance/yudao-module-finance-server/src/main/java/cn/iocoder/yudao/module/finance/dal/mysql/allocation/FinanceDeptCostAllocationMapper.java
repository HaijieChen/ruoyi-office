package cn.iocoder.yudao.module.finance.dal.mysql.allocation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.allocation.FinanceDeptCostAllocationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinanceDeptCostAllocationMapper extends BaseMapperX<FinanceDeptCostAllocationDO> {

    default List<FinanceDeptCostAllocationDO> selectByPeriodAndSourceType(String period, String sourceType) {
        return selectList(new LambdaQueryWrapperX<FinanceDeptCostAllocationDO>()
                .eq(FinanceDeptCostAllocationDO::getPeriod, period)
                .eq(FinanceDeptCostAllocationDO::getSourceType, sourceType)
                .orderByAsc(FinanceDeptCostAllocationDO::getId));
    }

    default int deleteByPeriodAndSourceType(String period, String sourceType) {
        return delete(new LambdaQueryWrapperX<FinanceDeptCostAllocationDO>()
                .eq(FinanceDeptCostAllocationDO::getPeriod, period)
                .eq(FinanceDeptCostAllocationDO::getSourceType, sourceType));
    }
}
