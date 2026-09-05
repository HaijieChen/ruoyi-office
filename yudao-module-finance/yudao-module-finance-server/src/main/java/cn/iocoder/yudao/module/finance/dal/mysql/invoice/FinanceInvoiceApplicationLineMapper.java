package cn.iocoder.yudao.module.finance.dal.mysql.invoice;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 开票申请明细 Mapper
 */
@Mapper
public interface FinanceInvoiceApplicationLineMapper extends BaseMapperX<FinanceInvoiceApplicationLineDO> {

    default List<FinanceInvoiceApplicationLineDO> selectListByApplicationId(Long applicationId) {
        return selectList(FinanceInvoiceApplicationLineDO::getApplicationId, applicationId);
    }

    default List<FinanceInvoiceApplicationLineDO> selectListByApplicationIds(Collection<Long> applicationIds) {
        if (CollUtil.isEmpty(applicationIds)) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<FinanceInvoiceApplicationLineDO>()
                .in(FinanceInvoiceApplicationLineDO::getApplicationId, applicationIds));
    }

    default int deleteByApplicationId(Long applicationId) {
        return delete(FinanceInvoiceApplicationLineDO::getApplicationId, applicationId);
    }

    default List<FinanceInvoiceApplicationLineDO> selectListBySourceContractApplicationId(Long contractId) {
        return selectList(FinanceInvoiceApplicationLineDO::getSourceContractApplicationId, contractId);
    }

}
