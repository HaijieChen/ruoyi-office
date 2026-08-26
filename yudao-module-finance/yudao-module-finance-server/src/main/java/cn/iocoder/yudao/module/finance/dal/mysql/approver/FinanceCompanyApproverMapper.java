package cn.iocoder.yudao.module.finance.dal.mysql.approver;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.dal.dataobject.approver.FinanceCompanyApproverDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinanceCompanyApproverMapper extends BaseMapperX<FinanceCompanyApproverDO> {

    default List<FinanceCompanyApproverDO> selectListByCompany(Long entityCompanyDeptId) {
        return selectList(new LambdaQueryWrapperX<FinanceCompanyApproverDO>()
                .eq(FinanceCompanyApproverDO::getEntityCompanyDeptId, entityCompanyDeptId)
                .orderByAsc(FinanceCompanyApproverDO::getId));
    }

    default void deleteByCompany(Long entityCompanyDeptId) {
        delete(new LambdaQueryWrapperX<FinanceCompanyApproverDO>()
                .eq(FinanceCompanyApproverDO::getEntityCompanyDeptId, entityCompanyDeptId));
    }
}
