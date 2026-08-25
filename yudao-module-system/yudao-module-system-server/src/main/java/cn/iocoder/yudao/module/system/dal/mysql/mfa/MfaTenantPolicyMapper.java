package cn.iocoder.yudao.module.system.dal.mysql.mfa;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaTenantPolicyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MfaTenantPolicyMapper extends BaseMapperX<MfaTenantPolicyDO> {

    default MfaTenantPolicyDO selectByTenantId(Long tenantId) {
        return selectOne(MfaTenantPolicyDO::getTenantId, tenantId);
    }

    default List<MfaTenantPolicyDO> selectAllConfirmed() {
        return selectList(MfaTenantPolicyDO::getConfirmed, true);
    }

}
