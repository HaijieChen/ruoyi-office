package cn.iocoder.yudao.module.bpm.dal.mysql.definition;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.datasource.BpmFormDataSourcePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDataSourceDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * BPM 表单数据源 Mapper
 *
 * @author 宇擎源码
 */
@Mapper
public interface BpmFormDataSourceMapper extends BaseMapperX<BpmFormDataSourceDO> {

    default BpmFormDataSourceDO selectByCode(String code) {
        return selectOne(BpmFormDataSourceDO::getCode, code);
    }

    default BpmFormDataSourceDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapper<BpmFormDataSourceDO>()
                .eq(BpmFormDataSourceDO::getId, id)
                .last("FOR UPDATE"));
    }

    default PageResult<BpmFormDataSourceDO> selectPage(BpmFormDataSourcePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmFormDataSourceDO>()
                .likeIfPresent(BpmFormDataSourceDO::getName, reqVO.getName())
                .likeIfPresent(BpmFormDataSourceDO::getCode, reqVO.getCode())
                .eqIfPresent(BpmFormDataSourceDO::getType, reqVO.getType())
                .eqIfPresent(BpmFormDataSourceDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(BpmFormDataSourceDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BpmFormDataSourceDO::getId));
    }

    default List<BpmFormDataSourceDO> selectEnabledPublishedList() {
        return selectList(new LambdaQueryWrapper<BpmFormDataSourceDO>()
                .eq(BpmFormDataSourceDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .isNotNull(BpmFormDataSourceDO::getPublishedVersion)
                .orderByAsc(BpmFormDataSourceDO::getName)
                .orderByAsc(BpmFormDataSourceDO::getId));
    }

}
