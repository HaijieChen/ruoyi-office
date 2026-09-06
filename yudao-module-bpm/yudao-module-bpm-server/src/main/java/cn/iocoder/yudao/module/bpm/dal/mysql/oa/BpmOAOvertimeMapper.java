package cn.iocoder.yudao.module.bpm.dal.mysql.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 加班申请 Mapper
 */
@Mapper
public interface BpmOAOvertimeMapper extends BaseMapperX<BpmOAOvertimeDO> {

    default PageResult<BpmOAOvertimeDO> selectPage(Long userId, BpmOAOvertimePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmOAOvertimeDO>()
                .eqIfPresent(BpmOAOvertimeDO::getUserId, userId)
                .eqIfPresent(BpmOAOvertimeDO::getStatus, reqVO.getStatus())
                .likeIfPresent(BpmOAOvertimeDO::getReason, reqVO.getReason())
                .betweenIfPresent(BpmOAOvertimeDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BpmOAOvertimeDO::getId));
    }

    /**
     * 锁定申请人该自然日加班行，供额度与重叠校验。
     */
    default List<BpmOAOvertimeDO> selectByUserAndDayForUpdate(Long userId,
                                                              LocalDateTime dayStart,
                                                              LocalDateTime dayEndExclusive) {
        return selectList(new LambdaQueryWrapper<BpmOAOvertimeDO>()
                .eq(BpmOAOvertimeDO::getUserId, userId)
                .ge(BpmOAOvertimeDO::getStartTime, dayStart)
                .lt(BpmOAOvertimeDO::getStartTime, dayEndExclusive)
                .last("FOR UPDATE"));
    }

}
