package cn.iocoder.yudao.module.hrm.dal.mysql.employee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.OnboardingFileClaimDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OnboardingFileClaimMapper extends BaseMapperX<OnboardingFileClaimDO> {

    default OnboardingFileClaimDO selectByClaimToken(String claimToken) {
        return selectOne(new LambdaQueryWrapperX<OnboardingFileClaimDO>()
                .eq(OnboardingFileClaimDO::getClaimToken, claimToken));
    }

}
