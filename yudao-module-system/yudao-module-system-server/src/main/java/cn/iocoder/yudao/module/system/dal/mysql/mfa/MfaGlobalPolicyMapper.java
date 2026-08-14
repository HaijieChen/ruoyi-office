package cn.iocoder.yudao.module.system.dal.mysql.mfa;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaGlobalPolicyDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MfaGlobalPolicyMapper extends BaseMapperX<MfaGlobalPolicyDO> {

    default MfaGlobalPolicyDO selectSingleton() {
        return selectById(MfaGlobalPolicyDO.SINGLETON_ID);
    }

}
