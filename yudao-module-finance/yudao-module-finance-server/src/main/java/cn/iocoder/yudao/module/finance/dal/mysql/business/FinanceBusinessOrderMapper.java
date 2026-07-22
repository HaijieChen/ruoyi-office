package cn.iocoder.yudao.module.finance.dal.mysql.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.MPJLambdaWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.business.vo.FinanceBusinessOrderPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.business.FinanceBusinessOrderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinanceBusinessOrderMapper extends BaseMapperX<FinanceBusinessOrderDO> {

    default PageResult<FinanceBusinessOrderDO> selectPage(FinanceBusinessOrderPageReqVO reqVO) {
        return selectPage(reqVO, new MPJLambdaWrapperX<FinanceBusinessOrderDO>()
                .likeIfPresent(FinanceBusinessOrderDO::getOrderNo, reqVO.getOrderNo())
                .likeIfPresent(FinanceBusinessOrderDO::getBusinessSubject, reqVO.getBusinessSubject())
                .likeIfPresent(FinanceBusinessOrderDO::getBusinessType, reqVO.getBusinessType())
                .likeIfPresent(FinanceBusinessOrderDO::getContractRef, reqVO.getContractRef())
                .likeIfPresent(FinanceBusinessOrderDO::getProjectRef, reqVO.getProjectRef())
                .eqIfPresent(FinanceBusinessOrderDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FinanceBusinessOrderDO::getCurrency, reqVO.getCurrency())
                .orderByDesc(FinanceBusinessOrderDO::getId));
    }

    default FinanceBusinessOrderDO selectByOrderNo(String orderNo) {
        return selectOne("order_no", orderNo);
    }

}
