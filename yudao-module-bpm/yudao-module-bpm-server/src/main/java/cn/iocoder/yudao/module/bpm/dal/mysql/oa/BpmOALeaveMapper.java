package cn.iocoder.yudao.module.bpm.dal.mysql.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOALeavePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOALeaveDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.Collection;

/**
 * 请假申请 Mapper
 *
 * @author jason
 * @author 宇擎源码
 */
@Mapper
public interface BpmOALeaveMapper extends BaseMapperX<BpmOALeaveDO> {

    default PageResult<BpmOALeaveDO> selectPage(Long userId, BpmOALeavePageReqVO reqVO) {
        return selectPageByUsers(java.util.Collections.singleton(userId), reqVO);
    }

    // null 仅由服务端 ALL 数据范围使用；空集合必须返回零行。
    default PageResult<BpmOALeaveDO> selectPageByUsers(Collection<Long> userIds, BpmOALeavePageReqVO reqVO) {
        LambdaQueryWrapperX<BpmOALeaveDO> query = new LambdaQueryWrapperX<>();
        query.in(userIds != null && !userIds.isEmpty(), BpmOALeaveDO::getUserId, userIds);
        query.apply(userIds != null && userIds.isEmpty(), "1 = 0");
        return selectPage(reqVO, query
                .eqIfPresent(BpmOALeaveDO::getStatus, reqVO.getStatus())
                .eqIfPresent(BpmOALeaveDO::getType, reqVO.getType())
                .likeIfPresent(BpmOALeaveDO::getReason, reqVO.getReason())
                .betweenIfPresent(BpmOALeaveDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BpmOALeaveDO::getId));
    }

}
