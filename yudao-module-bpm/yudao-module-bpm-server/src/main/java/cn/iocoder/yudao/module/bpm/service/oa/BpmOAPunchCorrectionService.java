package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAPunchCorrectionDO;
import jakarta.validation.Valid;

import java.time.LocalDate;

/**
 * 补卡申请 Service 接口
 */
public interface BpmOAPunchCorrectionService {

    /**
     * 创建补卡申请
     *
     * @param userId      用户编号
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createPunchCorrection(Long userId, @Valid BpmOAPunchCorrectionCreateReqVO createReqVO);

    /**
     * 更新补卡申请的状态
     *
     * @param id     编号
     * @param status 结果
     */
    void updatePunchCorrectionStatus(Long id, Integer status);

    /**
     * 按已回写 processInstanceId 精确匹配后更新状态；不匹配则 no-op。
     */
    void updatePunchCorrectionStatus(Long id, Integer status, String processInstanceId);

    /**
     * 获得补卡申请
     *
     * @param id     编号
     * @param userId 当前登录用户
     * @return 补卡申请
     */
    BpmOAPunchCorrectionDO getPunchCorrection(Long id, Long userId);

    /**
     * 获得补卡申请分页
     *
     * @param userId    用户编号
     * @param pageReqVO 分页查询
     * @return 补卡申请分页
     */
    PageResult<BpmOAPunchCorrectionDO> getPunchCorrectionPage(Long userId, BpmOAPunchCorrectionPageReqVO pageReqVO);

    /**
     * 获得指定补卡日期所在月的剩余次数（每月最多 2 次，按补卡日期所在月）。
     *
     * @param userId    用户编号
     * @param punchDate 补卡日期
     * @return 剩余次数，最小为 0
     */
    Integer getRemainingCount(Long userId, LocalDate punchDate);

}
