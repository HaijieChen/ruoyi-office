package cn.iocoder.yudao.module.finance.dal.mysql.invoice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationLineDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 开票申请明细 Mapper
 */
@Mapper
public interface FinanceInvoiceApplicationLineMapper extends BaseMapperX<FinanceInvoiceApplicationLineDO> {

    default List<FinanceInvoiceApplicationLineDO> selectListByApplicationId(Long applicationId) {
        return selectList(FinanceInvoiceApplicationLineDO::getApplicationId, applicationId);
    }

    default int deleteByApplicationId(Long applicationId) {
        return delete(FinanceInvoiceApplicationLineDO::getApplicationId, applicationId);
    }

}
