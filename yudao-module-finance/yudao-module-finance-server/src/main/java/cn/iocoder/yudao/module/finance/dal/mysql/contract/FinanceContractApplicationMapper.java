package cn.iocoder.yudao.module.finance.dal.mysql.contract;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.finance.controller.admin.contract.vo.FinanceContractApplicationPageReqVO;
import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FinanceContractApplicationMapper extends BaseMapperX<FinanceContractApplicationDO> {

    default PageResult<FinanceContractApplicationDO> selectPage(FinanceContractApplicationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FinanceContractApplicationDO>()
                .likeIfPresent(FinanceContractApplicationDO::getApplicationNo, reqVO.getApplicationNo())
                .eqIfPresent(FinanceContractApplicationDO::getApprovalStatus, reqVO.getApprovalStatus())
                .eqIfPresent(FinanceContractApplicationDO::getApplicantUserId, reqVO.getApplicantUserId())
                .eqIfPresent(FinanceContractApplicationDO::getSignCompany, reqVO.getSignCompany())
                .eqIfPresent(FinanceContractApplicationDO::getFileType, reqVO.getFileType())
                .likeIfPresent(FinanceContractApplicationDO::getCounterpartyName, reqVO.getCounterpartyName())
                .orderByDesc(FinanceContractApplicationDO::getId));
    }

    default FinanceContractApplicationDO selectByApplicationNo(String applicationNo) {
        return selectOne(FinanceContractApplicationDO::getApplicationNo, applicationNo);
    }
}
