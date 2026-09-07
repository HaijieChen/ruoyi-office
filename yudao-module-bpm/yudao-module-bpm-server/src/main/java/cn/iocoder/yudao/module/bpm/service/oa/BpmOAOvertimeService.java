package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeDO;
import jakarta.validation.Valid;

/**
 * 加班申请 Service 接口
 */
public interface BpmOAOvertimeService {

    /**
     * 创建加班申请
     *
     * @param userId      用户编号
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createOvertime(Long userId, @Valid BpmOAOvertimeCreateReqVO createReqVO);

    /**
     * 更新加班申请的状态
     *
     * @param id     编号
     * @param status 结果
     */
    void updateOvertimeStatus(Long id, Integer status);

    /**
     * 按已回写 processInstanceId 精确匹配后更新状态；不匹配则 no-op。
     */
    void updateOvertimeStatus(Long id, Integer status, String processInstanceId);

    /**
     * 获得加班申请
     *
     * @param id     编号
     * @param userId 当前登录用户
     * @return 加班申请
     */
    BpmOAOvertimeDO getOvertime(Long id, Long userId);

    /**
     * 获得加班申请分页
     *
     * @param userId    用户编号
     * @param pageReqVO 分页查询
     * @return 加班申请分页
     */
    PageResult<BpmOAOvertimeDO> getOvertimePage(Long userId, BpmOAOvertimePageReqVO pageReqVO);

}
