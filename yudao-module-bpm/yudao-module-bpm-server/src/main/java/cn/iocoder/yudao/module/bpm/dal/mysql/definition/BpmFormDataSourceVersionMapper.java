package cn.iocoder.yudao.module.bpm.dal.mysql.definition;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * BPM 表单数据源版本 Mapper
 *
 * @author 宇擎源码
 */
@Mapper
public interface BpmFormDataSourceVersionMapper extends BaseMapperX<BpmFormDataSourceVersionDO> {

    /**
     * 查询已发布的版本（status = 0 表示已发布）
     */
    default BpmFormDataSourceVersionDO selectPublished(Long dataSourceId) {
        return selectOne(new LambdaQueryWrapper<BpmFormDataSourceVersionDO>()
                .eq(BpmFormDataSourceVersionDO::getDataSourceId, dataSourceId)
                .eq(BpmFormDataSourceVersionDO::getStatus, 0)
                .orderByDesc(BpmFormDataSourceVersionDO::getVersion)
                .last("LIMIT 1"));
    }

    /**
     * 获取下一个版本号
     *
     * <p>注意：该计算不是原子操作。调用方必须在生命周期事务中串行化同一数据源的版本创建，
     * 并对唯一约束冲突进行明确处理，不得将本方法视为并发安全的发号器。
     */
    default Integer selectNextVersion(Long dataSourceId) {
        BpmFormDataSourceVersionDO maxVersion = selectOne(new LambdaQueryWrapper<BpmFormDataSourceVersionDO>()
                .eq(BpmFormDataSourceVersionDO::getDataSourceId, dataSourceId)
                .orderByDesc(BpmFormDataSourceVersionDO::getVersion)
                .last("LIMIT 1"));
        return maxVersion == null ? 1 : maxVersion.getVersion() + 1;
    }

}
