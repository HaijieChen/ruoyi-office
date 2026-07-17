package cn.iocoder.yudao.module.bpm.dal.mysql.definition;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceVersionDO;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmFormDataSourceVersionStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

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
                .eq(BpmFormDataSourceVersionDO::getStatus,
                        BpmFormDataSourceVersionStatusEnum.PUBLISHED.getStatus())
                .orderByDesc(BpmFormDataSourceVersionDO::getVersion)
                .last("LIMIT 1"));
    }

    default BpmFormDataSourceVersionDO selectByIdAndSourceId(Long id, Long dataSourceId) {
        return selectOne(new LambdaQueryWrapper<BpmFormDataSourceVersionDO>()
                .eq(BpmFormDataSourceVersionDO::getId, id)
                .eq(BpmFormDataSourceVersionDO::getDataSourceId, dataSourceId));
    }

    default BpmFormDataSourceVersionDO selectLatestDraft(Long dataSourceId) {
        return selectOne(new LambdaQueryWrapper<BpmFormDataSourceVersionDO>()
                .eq(BpmFormDataSourceVersionDO::getDataSourceId, dataSourceId)
                .eq(BpmFormDataSourceVersionDO::getStatus, BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus())
                .orderByDesc(BpmFormDataSourceVersionDO::getVersion)
                .last("LIMIT 1"));
    }

    default List<BpmFormDataSourceVersionDO> selectListBySourceId(Long dataSourceId) {
        return selectList(new LambdaQueryWrapper<BpmFormDataSourceVersionDO>()
                .eq(BpmFormDataSourceVersionDO::getDataSourceId, dataSourceId)
                .orderByDesc(BpmFormDataSourceVersionDO::getVersion));
    }

    default long selectCountBySourceId(Long dataSourceId) {
        return selectCount(new LambdaQueryWrapper<BpmFormDataSourceVersionDO>()
                .eq(BpmFormDataSourceVersionDO::getDataSourceId, dataSourceId));
    }

    /** Updates every configurable draft field, including clearing nullable fields back to platform defaults. */
    default int updateDraft(BpmFormDataSourceVersionDO draft) {
        return update(null, new LambdaUpdateWrapper<BpmFormDataSourceVersionDO>()
                .set(BpmFormDataSourceVersionDO::getSourceConfig, draft.getSourceConfig())
                .set(BpmFormDataSourceVersionDO::getParameterSchema, draft.getParameterSchema())
                .set(BpmFormDataSourceVersionDO::getResultSchema, draft.getResultSchema())
                .set(BpmFormDataSourceVersionDO::getLabelField, draft.getLabelField())
                .set(BpmFormDataSourceVersionDO::getValueField, draft.getValueField())
                .set(BpmFormDataSourceVersionDO::getPageable, draft.getPageable())
                .set(BpmFormDataSourceVersionDO::getMaxRows, draft.getMaxRows())
                .set(BpmFormDataSourceVersionDO::getTimeoutSeconds, draft.getTimeoutSeconds())
                .set(BpmFormDataSourceVersionDO::getCacheSeconds, draft.getCacheSeconds())
                .eq(BpmFormDataSourceVersionDO::getId, draft.getId())
                .eq(BpmFormDataSourceVersionDO::getDataSourceId, draft.getDataSourceId())
                .eq(BpmFormDataSourceVersionDO::getStatus,
                        BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus()));
    }

    /** Publishes a draft only when it is still mutable. Returns zero if another transition won the race. */
    default int publishDraft(Long id, Long dataSourceId) {
        return update(null, new LambdaUpdateWrapper<BpmFormDataSourceVersionDO>()
                .set(BpmFormDataSourceVersionDO::getStatus,
                        BpmFormDataSourceVersionStatusEnum.PUBLISHED.getStatus())
                .eq(BpmFormDataSourceVersionDO::getId, id)
                .eq(BpmFormDataSourceVersionDO::getDataSourceId, dataSourceId)
                .eq(BpmFormDataSourceVersionDO::getStatus,
                        BpmFormDataSourceVersionStatusEnum.DRAFT.getStatus()));
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
