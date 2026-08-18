package cn.iocoder.yudao.module.bpm.service.oa;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOutingPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOutingDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOutingMapper;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_DURATION_INVALID;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OUTING_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OUTING_NOT_EXISTS;

/**
 * OA 外出申请 Service 实现类
 */
@Service
@Validated
public class BpmOAOutingServiceImpl implements BpmOAOutingService {

    /**
     * OA 外出对应的流程定义 KEY
     */
    public static final String PROCESS_KEY = "oa_outing";

    private static final String QUERY_PERMISSION = "bpm:oa-outing:query";

    @Resource
    private BpmOAOutingMapper outingMapper;

    @Resource
    private BpmProcessInstanceApi processInstanceApi;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Resource
    private OaBillAccessPermission oaBillAccessPermission;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOuting(Long userId, BpmOAOutingCreateReqVO createReqVO) {
        if (StrUtil.isBlank(createReqVO.getReason())) {
            throw invalidParamException("外出事由不能为空");
        }
        if (StrUtil.isBlank(createReqVO.getLocation())) {
            throw invalidParamException("外出地点不能为空");
        }
        BigDecimal hours = OaDurationHours.calc(createReqVO.getStartTime(), createReqVO.getEndTime())
                .orElseThrow(() -> exception(OA_DURATION_INVALID));

        BpmOAOutingDO outing = BeanUtils.toBean(createReqVO, BpmOAOutingDO.class)
                .setUserId(userId)
                .setHours(hours)
                .setStatus(BpmTaskStatusEnum.RUNNING.getStatus())
                .setAttendanceSyncStatus(OaAttendanceSyncStatusEnum.NOT_SYNCED.getStatus());
        outingMapper.insert(outing);

        Map<String, Object> processInstanceVariables = new HashMap<>();
        processInstanceVariables.put("hours", hours);
        processInstanceVariables.put("need_output", createReqVO.getNeedOutput());
        String processInstanceId = processInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY)
                        .setVariables(processInstanceVariables).setBusinessKey(String.valueOf(outing.getId())))
                .getCheckedData();

        outingMapper.updateById(new BpmOAOutingDO().setId(outing.getId()).setProcessInstanceId(processInstanceId));
        return outing.getId();
    }

    @Override
    public void updateOutingStatus(Long id, Integer status) {
        validateOutingExists(id);
        outingMapper.updateById(new BpmOAOutingDO().setId(id).setStatus(status));
    }

    private void validateOutingExists(Long id) {
        if (outingMapper.selectById(id) == null) {
            throw exception(OA_OUTING_NOT_EXISTS);
        }
    }

    @Override
    public BpmOAOutingDO getOuting(Long id, Long userId) {
        BpmOAOutingDO outing = outingMapper.selectById(id);
        if (outing == null) {
            throw exception(OA_OUTING_NOT_EXISTS);
        }
        if (java.util.Objects.equals(outing.getUserId(), userId)
                || securityFrameworkService.hasPermission(QUERY_PERMISSION)
                || oaBillAccessPermission.isActiveTaskCandidateOrAssignee(outing.getProcessInstanceId(), userId)) {
            return outing;
        }
        throw exception(OA_OUTING_ACCESS_DENIED);
    }

    @Override
    public PageResult<BpmOAOutingDO> getOutingPage(Long userId, BpmOAOutingPageReqVO pageReqVO) {
        Long filterUserId = securityFrameworkService.hasPermission(QUERY_PERMISSION) ? null : userId;
        return outingMapper.selectPage(filterUserId, pageReqVO);
    }

}
