package cn.iocoder.yudao.module.finance.dal.mysql.contract;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FinanceContractApplicationMapper extends BaseMapperX<FinanceContractApplicationDO> {

    default LambdaQueryWrapperX<FinanceContractApplicationDO> buildQuery(FinanceContractApplicationPageReqVO reqVO) {
        return new LambdaQueryWrapperX<FinanceContractApplicationDO>()
                .likeIfPresent(FinanceContractApplicationDO::getApplicationNo, reqVO.getApplicationNo())
                .eqIfPresent(FinanceContractApplicationDO::getApprovalStatus, reqVO.getApprovalStatus())
                .eqIfPresent(FinanceContractApplicationDO::getApplicantUserId, reqVO.getApplicantUserId())
                .eqIfPresent(FinanceContractApplicationDO::getSignCompany, reqVO.getSignCompany())
                .eqIfPresent(FinanceContractApplicationDO::getEntityCompanyDeptId, reqVO.getEntityCompanyDeptId())
                .eqIfPresent(FinanceContractApplicationDO::getCurrency, reqVO.getCurrency())
                .eqIfPresent(FinanceContractApplicationDO::getFileType, reqVO.getFileType())
                .eqIfPresent(FinanceContractApplicationDO::getProductType, reqVO.getProductType())
                .likeIfPresent(FinanceContractApplicationDO::getCounterpartyName, reqVO.getCounterpartyName())
                .eqIfPresent(FinanceContractApplicationDO::getCounterpartyCompanyId, reqVO.getCounterpartyCompanyId())
                .orderByDesc(FinanceContractApplicationDO::getId);
    }

    default PageResult<FinanceContractApplicationDO> selectPage(FinanceContractApplicationPageReqVO reqVO) {
        return selectPage(reqVO, buildQuery(reqVO));
    }

    default List<FinanceContractApplicationDO> selectList(FinanceContractApplicationPageReqVO reqVO) {
        return selectList(buildQuery(reqVO));
    }

    default FinanceContractApplicationDO selectByApplicationNo(String applicationNo) {
        return selectOne(FinanceContractApplicationDO::getApplicationNo, applicationNo);
    }
}
