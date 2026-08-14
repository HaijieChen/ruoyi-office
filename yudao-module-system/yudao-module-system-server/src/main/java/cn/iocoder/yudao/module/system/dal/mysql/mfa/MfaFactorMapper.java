package cn.iocoder.yudao.module.system.dal.mysql.mfa;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaFactorDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MfaFactorMapper extends BaseMapperX<MfaFactorDO> {

    default MfaFactorDO selectByUserAndKey(Long tenantId, Long userId, String factorKey) {
        return selectOne(MfaFactorDO::getTenantId, tenantId,
                MfaFactorDO::getUserId, userId,
                MfaFactorDO::getFactorKey, factorKey);
    }

    default List<MfaFactorDO> selectByUser(Long tenantId, Long userId) {
        return selectList(MfaFactorDO::getTenantId, tenantId, MfaFactorDO::getUserId, userId);
    }

    @Update("UPDATE system_mfa_factor SET status = #{toStatus}, "
            + "last_used_step = COALESCE(#{lastUsedStep}, last_used_step) "
            + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND factor_key = #{factorKey} "
            + "AND status = #{fromStatus} AND deleted = 0")
    int casStatus(@Param("tenantId") Long tenantId,
                  @Param("userId") Long userId,
                  @Param("factorKey") String factorKey,
                  @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus,
                  @Param("lastUsedStep") Long lastUsedStep);

    @Update("UPDATE system_mfa_factor SET last_used_step = #{step} "
            + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND factor_key = #{factorKey} "
            + "AND deleted = 0 AND (last_used_step IS NULL OR last_used_step < #{step})")
    int claimTotpStep(@Param("tenantId") Long tenantId,
                      @Param("userId") Long userId,
                      @Param("factorKey") String factorKey,
                      @Param("step") long step);
}
