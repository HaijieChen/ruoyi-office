package cn.iocoder.yudao.module.system.dal.mysql.mfa;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaAuthFlowDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MfaAuthFlowMapper extends BaseMapperX<MfaAuthFlowDO> {

    default MfaAuthFlowDO selectByTokenHash(String flowTokenHash) {
        return selectOne(MfaAuthFlowDO::getFlowTokenHash, flowTokenHash);
    }

    @Update("UPDATE system_mfa_auth_flow SET state = #{toState} "
            + "WHERE flow_token_hash = #{hash} AND state = #{fromState} AND deleted = 0")
    int casState(@Param("hash") String hash,
                 @Param("fromState") String fromState,
                 @Param("toState") String toState);
}
