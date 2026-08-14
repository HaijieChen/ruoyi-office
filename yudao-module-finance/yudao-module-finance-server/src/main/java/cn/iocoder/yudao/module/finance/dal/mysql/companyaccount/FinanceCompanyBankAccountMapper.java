package cn.iocoder.yudao.module.finance.dal.mysql.companyaccount;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.companyaccount.vo.FinanceCompanyBankAccountPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.companyaccount.FinanceCompanyBankAccountDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinanceCompanyBankAccountMapper extends BaseMapperX<FinanceCompanyBankAccountDO> {

    default PageResult<FinanceCompanyBankAccountDO> selectPage(FinanceCompanyBankAccountPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceCompanyBankAccountDO>()
                .eqIfPresent(FinanceCompanyBankAccountDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .likeIfPresent(FinanceCompanyBankAccountDO::getAccountName, reqVO.getAccountName())
                .likeIfPresent(FinanceCompanyBankAccountDO::getBankName, reqVO.getBankName())
                .likeIfPresent(FinanceCompanyBankAccountDO::getAccountNo, reqVO.getAccountNo())
                .eqIfPresent(FinanceCompanyBankAccountDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FinanceCompanyBankAccountDO::getCurrency, reqVO.getCurrency())
                .orderByDesc(FinanceCompanyBankAccountDO::getId));
    }

    default List<FinanceCompanyBankAccountDO> selectEnabledByEntityCompany(Long entityCompanyDeptId) {
        return selectList(new LambdaQueryWrapperX<FinanceCompanyBankAccountDO>()
                .eq(FinanceCompanyBankAccountDO::getEntityCompanyDeptId, entityCompanyDeptId)
                .eq(FinanceCompanyBankAccountDO::getStatus, FinanceCompanyBankAccountDO.STATUS_ENABLE)
                .orderByDesc(FinanceCompanyBankAccountDO::getId));
    }

    default FinanceCompanyBankAccountDO selectByCompanyAndAccountNo(Long entityCompanyDeptId, String accountNo) {
        return selectOne(new LambdaQueryWrapperX<FinanceCompanyBankAccountDO>()
                .eq(FinanceCompanyBankAccountDO::getEntityCompanyDeptId, entityCompanyDeptId)
                .eq(FinanceCompanyBankAccountDO::getAccountNo, accountNo));
    }

}
