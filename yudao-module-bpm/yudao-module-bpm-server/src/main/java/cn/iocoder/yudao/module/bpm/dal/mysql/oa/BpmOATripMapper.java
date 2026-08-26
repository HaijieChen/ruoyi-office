package cn.iocoder.yudao.module.bpm.dal.mysql.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOATripPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOATripDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出差申请 Mapper
 */
@Mapper
public interface BpmOATripMapper extends BaseMapperX<BpmOATripDO> {

    default PageResult<BpmOATripDO> selectPage(Long userId, BpmOATripPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmOATripDO>()
                .eqIfPresent(BpmOATripDO::getUserId, userId)
                .eqIfPresent(BpmOATripDO::getStatus, reqVO.getStatus())
                .eqIfPresent(BpmOATripDO::getBizType, reqVO.getBizType())
                .betweenIfPresent(BpmOATripDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BpmOATripDO::getId));
    }

}
