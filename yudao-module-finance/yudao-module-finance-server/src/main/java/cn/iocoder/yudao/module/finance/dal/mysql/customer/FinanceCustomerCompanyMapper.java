package cn.iocoder.yudao.module.finance.dal.mysql.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.customer.vo.FinanceCustomerCompanyPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.customer.FinanceCustomerCompanyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinanceCustomerCompanyMapper extends BaseMapperX<FinanceCustomerCompanyDO> {

    default PageResult<FinanceCustomerCompanyDO> selectPage(FinanceCustomerCompanyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceCustomerCompanyDO>()
                .likeIfPresent(FinanceCustomerCompanyDO::getName, reqVO.getName())
                .likeIfPresent(FinanceCustomerCompanyDO::getTaxNo, reqVO.getTaxNo())
                .likeIfPresent(FinanceCustomerCompanyDO::getCode, reqVO.getCode())
                .eqIfPresent(FinanceCustomerCompanyDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FinanceCustomerCompanyDO::getIsCustomer, reqVO.getIsCustomer())
                .eqIfPresent(FinanceCustomerCompanyDO::getIsSupplier, reqVO.getIsSupplier())
                .orderByDesc(FinanceCustomerCompanyDO::getId));
    }

    default FinanceCustomerCompanyDO selectByTaxNo(String taxNo) {
        return selectOne(FinanceCustomerCompanyDO::getTaxNo, taxNo);
    }

    /** @deprecated 使用 {@link #selectEnabledCustomerList()} */
    default List<FinanceCustomerCompanyDO> selectEnabledList() {
        return selectEnabledCustomerList();
    }

    default List<FinanceCustomerCompanyDO> selectEnabledCustomerList() {
        return selectList(new LambdaQueryWrapperX<FinanceCustomerCompanyDO>()
                .eq(FinanceCustomerCompanyDO::getStatus, FinanceCustomerCompanyDO.STATUS_ENABLE)
                .and(w -> w.eq(FinanceCustomerCompanyDO::getIsCustomer, true)
                        .or()
                        .isNull(FinanceCustomerCompanyDO::getIsCustomer))
                .orderByDesc(FinanceCustomerCompanyDO::getId));
    }

    default List<FinanceCustomerCompanyDO> selectEnabledSupplierList() {
        return selectList(new LambdaQueryWrapperX<FinanceCustomerCompanyDO>()
                .eq(FinanceCustomerCompanyDO::getStatus, FinanceCustomerCompanyDO.STATUS_ENABLE)
                .eq(FinanceCustomerCompanyDO::getIsSupplier, true)
                .isNotNull(FinanceCustomerCompanyDO::getBankName)
                .ne(FinanceCustomerCompanyDO::getBankName, "")
                .isNotNull(FinanceCustomerCompanyDO::getBankAccount)
                .ne(FinanceCustomerCompanyDO::getBankAccount, "")
                .orderByDesc(FinanceCustomerCompanyDO::getId));
    }

}
