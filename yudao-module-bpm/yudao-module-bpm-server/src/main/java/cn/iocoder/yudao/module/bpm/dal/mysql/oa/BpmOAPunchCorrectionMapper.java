package cn.iocoder.yudao.module.bpm.dal.mysql.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAPunchCorrectionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 补卡申请 Mapper
 */
@Mapper
public interface BpmOAPunchCorrectionMapper extends BaseMapperX<BpmOAPunchCorrectionDO> {

    default PageResult<BpmOAPunchCorrectionDO> selectPage(Long userId, BpmOAPunchCorrectionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmOAPunchCorrectionDO>()
                .eqIfPresent(BpmOAPunchCorrectionDO::getUserId, userId)
                .eqIfPresent(BpmOAPunchCorrectionDO::getStatus, reqVO.getStatus())
                .likeIfPresent(BpmOAPunchCorrectionDO::getReason, reqVO.getReason())
                .betweenIfPresent(BpmOAPunchCorrectionDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BpmOAPunchCorrectionDO::getId));
    }

    /**
     * 锁定申请人该自然月补卡行，供月次数校验。
     */
    default List<BpmOAPunchCorrectionDO> selectByUserAndMonthForUpdate(Long userId,
                                                                       LocalDate monthStart,
                                                                       LocalDate monthEndExclusive) {
        return selectList(new LambdaQueryWrapper<BpmOAPunchCorrectionDO>()
                .eq(BpmOAPunchCorrectionDO::getUserId, userId)
                .ge(BpmOAPunchCorrectionDO::getPunchDate, monthStart)
                .lt(BpmOAPunchCorrectionDO::getPunchDate, monthEndExclusive)
                .last("FOR UPDATE"));
    }

    /**
     * 查询申请人该自然月补卡行（剩余次数，不加锁）。
     */
    default List<BpmOAPunchCorrectionDO> selectByUserAndMonth(Long userId,
                                                              LocalDate monthStart,
                                                              LocalDate monthEndExclusive) {
        return selectList(new LambdaQueryWrapper<BpmOAPunchCorrectionDO>()
                .eq(BpmOAPunchCorrectionDO::getUserId, userId)
                .ge(BpmOAPunchCorrectionDO::getPunchDate, monthStart)
                .lt(BpmOAPunchCorrectionDO::getPunchDate, monthEndExclusive));
    }

}
