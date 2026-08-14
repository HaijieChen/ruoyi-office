package cn.iocoder.yudao.module.system.dal.mysql.mfa;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.mfa.MfaControlStateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MfaControlStateMapper extends BaseMapperX<MfaControlStateDO> {

    default MfaControlStateDO selectSingleton() {
        return selectById(MfaControlStateDO.SINGLETON_ID);
    }

}
