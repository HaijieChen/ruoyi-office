package cn.iocoder.yudao.module.bpm.dal.mysql.definition;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * BPM 表单数据源 Mapper
 *
 * @author 宇擎源码
 */
@Mapper
public interface BpmFormDataSourceMapper extends BaseMapperX<BpmFormDataSourceDO> {

    default BpmFormDataSourceDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<BpmFormDataSourceDO>()
                .eq(BpmFormDataSourceDO::getCode, code));
    }

}
