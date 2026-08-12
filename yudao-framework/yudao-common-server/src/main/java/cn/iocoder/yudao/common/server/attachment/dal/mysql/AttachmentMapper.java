package cn.iocoder.yudao.common.server.attachment.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.common.server.attachment.dal.dataobject.AttachmentDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 通用附件信息 Mapper
 *
 * @author 宇擎源码
 */
@Mapper
public interface AttachmentMapper extends BaseMapperX<AttachmentDO> {

    /**
     * 根据业务类型和业务ID查询附件列表
     *
     * @param businessType 业务类型
     * @param businessId 业务ID
     * @return 附件列表
     */
    default List<AttachmentDO> selectListByBusiness(String businessType, Long businessId) {
        return selectList(new LambdaQueryWrapper<AttachmentDO>()
                .eq(AttachmentDO::getBusinessType, businessType)
                .eq(AttachmentDO::getBusinessId, businessId)
                .orderByAsc(AttachmentDO::getSortOrder)
                .orderByAsc(AttachmentDO::getCreateTime));
    }

    /**
     * 根据业务类型和业务ID集合批量查询附件
     *
     * @param businessType 业务类型
     * @param businessIds 业务ID集合
     * @return 附件列表
     */
    default List<AttachmentDO> selectListByBusinessIds(String businessType, Collection<Long> businessIds) {
        if (businessIds == null || businessIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapper<AttachmentDO>()
                .eq(AttachmentDO::getBusinessType, businessType)
                .in(AttachmentDO::getBusinessId, businessIds)
                .orderByAsc(AttachmentDO::getBusinessId)
                .orderByAsc(AttachmentDO::getSortOrder)
                .orderByAsc(AttachmentDO::getCreateTime));
    }

    /**
     * 根据业务类型和业务ID删除附件
     *
     * @param businessType 业务类型
     * @param businessId 业务ID
     */
    default void deleteByBusiness(String businessType, Long businessId) {
        delete(new LambdaQueryWrapper<AttachmentDO>()
                .eq(AttachmentDO::getBusinessType, businessType)
                .eq(AttachmentDO::getBusinessId, businessId));
    }

    /**
     * 根据业务类型和业务ID列表删除附件
     *
     * @param businessType 业务类型
     * @param businessIds 业务ID列表
     */
    default void deleteByBusinessIds(String businessType, List<Long> businessIds) {
        delete(new LambdaQueryWrapper<AttachmentDO>()
                .eq(AttachmentDO::getBusinessType, businessType)
                .in(AttachmentDO::getBusinessId, businessIds));
    }

}
