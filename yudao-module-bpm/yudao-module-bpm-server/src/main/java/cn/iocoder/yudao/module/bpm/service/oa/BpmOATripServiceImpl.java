package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOATripCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOATripPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOATripDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOATripMapper;
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
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_DURATION_INVALID;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_TRIP_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_TRIP_COMPANION_INVALID;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_TRIP_FIELD_REQUIRED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_TRIP_NOT_EXISTS;

/**
 * OA 出差申请 Service 实现类
 */
@Service
@Validated
public class BpmOATripServiceImpl implements BpmOATripService {

    /**
     * OA 出差对应的流程定义 KEY
     */
    public static final String PROCESS_KEY = "oa_business_trip";

    public static final String QUERY_PERMISSION = "bpm:oa-trip:query";

    @Resource
    private BpmOATripMapper tripMapper;

    @Resource
    private BpmProcessInstanceApi processInstanceApi;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Resource
    private OaBillAccessPermission oaBillAccessPermission;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTrip(Long userId, BpmOATripCreateReqVO createReqVO) {
        java.util.List<Long> companionIds = resolveCompanionIds(createReqVO);
        if (StrUtil.isBlank(createReqVO.getDestination()) || StrUtil.isBlank(createReqVO.getReason())
                || companionIds.isEmpty()) {
            throw exception(OA_TRIP_FIELD_REQUIRED);
        }
        if (companionIds.contains(userId)) {
            throw exception(OA_TRIP_COMPANION_INVALID);
        }
        CommonResult<java.util.List<AdminUserRespDTO>> companions = adminUserApi.getUserList(companionIds);
        java.util.List<AdminUserRespDTO> companionUsers = companions == null ? null : companions.getData();
        if (companionUsers == null || companionUsers.size() != companionIds.size()) {
            throw exception(OA_TRIP_COMPANION_INVALID);
        }
        createReqVO.setCompanionUserIds(companionIds);
        createReqVO.setCompanionUserId(companionIds.get(0));

        BigDecimal hours = OaDurationHours.calc(createReqVO.getStartTime(), createReqVO.getEndTime())
                .orElseThrow(() -> exception(OA_DURATION_INVALID));

        BpmOATripDO trip = BeanUtils.toBean(createReqVO, BpmOATripDO.class)
                .setUserId(userId)
                .setHours(hours)
                .setStatus(BpmTaskStatusEnum.RUNNING.getStatus())
                .setAttendanceSyncStatus(OaAttendanceSyncStatusEnum.NOT_SYNCED.getStatus());
        trip.setCompanionUserIds(companionIds);
        trip.setCompanionUserId(companionIds.get(0));
        tripMapper.insert(trip);

        Map<String, Object> processInstanceVariables = new HashMap<>();
        processInstanceVariables.put("hours", hours);
        processInstanceVariables.put("destination", createReqVO.getDestination().trim());
        if (createReqVO.getStartCompanyDeptId() != null) {
            processInstanceVariables.put("startCompanyDeptId", createReqVO.getStartCompanyDeptId());
        }
        if (createReqVO.getStartDeptId() != null) {
            processInstanceVariables.put("startDeptId", createReqVO.getStartDeptId());
        }
        if (createReqVO.getType() != null) {
            processInstanceVariables.put("type", createReqVO.getType());
        }
        String processInstanceId = processInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY)
                        .setVariables(processInstanceVariables).setBusinessKey(String.valueOf(trip.getId())))
                .getCheckedData();

        tripMapper.updateById(new BpmOATripDO().setId(trip.getId()).setProcessInstanceId(processInstanceId));
        return trip.getId();
    }

    @Override
    public void updateTripStatus(Long id, Integer status) {
        validateTripExists(id);
        tripMapper.updateById(new BpmOATripDO().setId(id).setStatus(status));
    }

    private void validateTripExists(Long id) {
        if (tripMapper.selectById(id) == null) {
            throw exception(OA_TRIP_NOT_EXISTS);
        }
    }

    @Override
    public BpmOATripDO getTrip(Long id, Long userId) {
        BpmOATripDO trip = tripMapper.selectById(id);
        if (trip == null) {
            throw exception(OA_TRIP_NOT_EXISTS);
        }
        if (Objects.equals(trip.getUserId(), userId)
                || securityFrameworkService.hasPermission(QUERY_PERMISSION)
                || oaBillAccessPermission.isActiveTaskCandidateOrAssignee(trip.getProcessInstanceId(), userId)) {
            return trip;
        }
        throw exception(OA_TRIP_ACCESS_DENIED);
    }

    @Override
    public PageResult<BpmOATripDO> getTripPage(Long userId, BpmOATripPageReqVO pageReqVO) {
        Long filterUserId = securityFrameworkService.hasPermission(QUERY_PERMISSION) ? null : userId;
        return tripMapper.selectPage(filterUserId, pageReqVO);
    }

    static java.util.List<Long> resolveCompanionIds(BpmOATripCreateReqVO vo) {
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        if (vo.getCompanionUserIds() != null) {
            for (Long id : vo.getCompanionUserIds()) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty() && vo.getCompanionUserId() != null) {
            ids.add(vo.getCompanionUserId());
        }
        return new java.util.ArrayList<>(ids);
    }

}
