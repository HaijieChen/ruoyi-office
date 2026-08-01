package cn.iocoder.yudao.module.finance.dal.mysql.invoice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.invoice.FinanceInvoiceApplicationFileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 开票申请办票附件 Mapper
 */
@Mapper
public interface FinanceInvoiceApplicationFileMapper extends BaseMapperX<FinanceInvoiceApplicationFileDO> {

    default List<FinanceInvoiceApplicationFileDO> selectListByApplicationId(Long applicationId) {
        return selectList(FinanceInvoiceApplicationFileDO::getApplicationId, applicationId);
    }

    default int deleteByApplicationId(Long applicationId) {
        return delete(FinanceInvoiceApplicationFileDO::getApplicationId, applicationId);
    }

}
