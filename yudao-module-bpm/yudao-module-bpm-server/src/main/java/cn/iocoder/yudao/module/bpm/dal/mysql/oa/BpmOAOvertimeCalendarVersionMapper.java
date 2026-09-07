package cn.iocoder.yudao.module.bpm.dal.mysql.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCalendarPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeCalendarVersionDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface BpmOAOvertimeCalendarVersionMapper extends BaseMapperX<BpmOAOvertimeCalendarVersionDO> {

    default BpmOAOvertimeCalendarVersionDO selectActiveByYear(int year) {
        return selectOne(new LambdaQueryWrapperX<BpmOAOvertimeCalendarVersionDO>()
                .eq(BpmOAOvertimeCalendarVersionDO::getCalendarYear, year)
                .eq(BpmOAOvertimeCalendarVersionDO::getStatus, BpmOAOvertimeCalendarVersionDO.ACTIVE)
                .last("LIMIT 1"));
    }

    default BpmOAOvertimeCalendarVersionDO selectByYearAndHash(int year, String contentHash) {
        return selectOne(new LambdaQueryWrapperX<BpmOAOvertimeCalendarVersionDO>()
                .eq(BpmOAOvertimeCalendarVersionDO::getCalendarYear, year)
                .eq(BpmOAOvertimeCalendarVersionDO::getContentHash, contentHash)
                .last("LIMIT 1"));
    }

    default BpmOAOvertimeCalendarVersionDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<BpmOAOvertimeCalendarVersionDO>()
                .eq(BpmOAOvertimeCalendarVersionDO::getId, id)
                .last("FOR UPDATE"));
    }

    default PageResult<BpmOAOvertimeCalendarVersionDO> selectPage(BpmOAOvertimeCalendarPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmOAOvertimeCalendarVersionDO>()
                .eqIfPresent(BpmOAOvertimeCalendarVersionDO::getCalendarYear, reqVO.getCalendarYear())
                .eqIfPresent(BpmOAOvertimeCalendarVersionDO::getStatus, reqVO.getStatus())
                .orderByDesc(BpmOAOvertimeCalendarVersionDO::getId));
    }
}
