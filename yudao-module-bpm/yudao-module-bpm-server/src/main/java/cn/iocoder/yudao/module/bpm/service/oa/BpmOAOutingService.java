package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOutingDO;
import jakarta.validation.Valid;

/**
 * 外出申请 Service 接口
 */
public interface BpmOAOutingService {

    /**
     * 创建外出申请
     *
     * @param userId      用户编号
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createOuting(Long userId, @Valid BpmOAOutingCreateReqVO createReqVO);

    /**
     * 更新外出申请的状态
     *
     * @param id     编号
     * @param status 结果
     */
    void updateOutingStatus(Long id, Integer status);

    /**
     * 获得外出申请
     *
     * @param id     编号
     * @param userId 当前登录用户
     * @return 外出申请
     */
    BpmOAOutingDO getOuting(Long id, Long userId);

    /**
     * 获得外出申请分页
     *
     * @param userId    用户编号
     * @param pageReqVO 分页查询
     * @return 外出申请分页
     */
    PageResult<BpmOAOutingDO> getOutingPage(Long userId, BpmOAOutingPageReqVO pageReqVO);

}
