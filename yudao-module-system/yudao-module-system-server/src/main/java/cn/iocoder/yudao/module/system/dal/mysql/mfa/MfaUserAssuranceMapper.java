package cn.iocoder.yudao.module.system.dal.mysql.mfa;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaUserAssuranceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MfaUserAssuranceMapper extends BaseMapperX<MfaUserAssuranceDO> {

    default MfaUserAssuranceDO selectByTenantAndUser(Long tenantId, Long userId) {
        return selectOne(MfaUserAssuranceDO::getTenantId, tenantId,
                MfaUserAssuranceDO::getUserId, userId);
    }

}
