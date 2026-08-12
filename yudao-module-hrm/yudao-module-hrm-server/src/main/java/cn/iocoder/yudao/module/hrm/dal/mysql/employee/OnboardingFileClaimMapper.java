package cn.iocoder.yudao.module.hrm.dal.mysql.employee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.hrm.dal.dataobject.employee.OnboardingFileClaimDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OnboardingFileClaimMapper extends BaseMapperX<OnboardingFileClaimDO> {

    default OnboardingFileClaimDO selectByClaimToken(String claimToken) {
        return selectOne(new LambdaQueryWrapperX<OnboardingFileClaimDO>()
                .eq(OnboardingFileClaimDO::getClaimToken, claimToken));
    }

    /**
     * 原子消费：token + 上传者 + 用途 + 未过期 + 未消费；tenant 由多租户插件行级附加。
     *
     * @return 影响行数（成功必须为 1）
     */
    @Update("UPDATE hrm_onboarding_file_claim "
            + "SET consumed_at = #{now}, consumed_employee_id = #{employeeId}, "
            + "updater = updater, update_time = #{now} "
            + "WHERE claim_token = #{claimToken} "
            + "AND uploader_user_id = #{uploaderUserId} "
            + "AND purpose = #{purpose} "
            + "AND expire_time > #{now} "
            + "AND consumed_at IS NULL "
            + "AND deleted = 0")
    int consumeIfOpen(@Param("claimToken") String claimToken,
                      @Param("uploaderUserId") Long uploaderUserId,
                      @Param("purpose") String purpose,
                      @Param("employeeId") Long employeeId,
                      @Param("now") LocalDateTime now);

}
