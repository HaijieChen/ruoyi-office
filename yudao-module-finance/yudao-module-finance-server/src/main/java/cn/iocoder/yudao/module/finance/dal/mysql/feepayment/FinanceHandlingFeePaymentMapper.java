package cn.iocoder.yudao.module.finance.dal.mysql.feepayment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.feepayment.vo.FinanceHandlingFeePaymentPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.feepayment.FinanceHandlingFeePaymentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinanceHandlingFeePaymentMapper extends BaseMapperX<FinanceHandlingFeePaymentDO> {

    default PageResult<FinanceHandlingFeePaymentDO> selectPage(FinanceHandlingFeePaymentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceHandlingFeePaymentDO>()
                .betweenIfPresent(FinanceHandlingFeePaymentDO::getFeeDate, reqVO.getFeeDate())
                .eqIfPresent(FinanceHandlingFeePaymentDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .orderByDesc(FinanceHandlingFeePaymentDO::getId));
    }

}
