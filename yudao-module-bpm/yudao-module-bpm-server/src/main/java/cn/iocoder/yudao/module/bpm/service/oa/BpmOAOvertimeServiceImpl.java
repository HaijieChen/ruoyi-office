package cn.iocoder.yudao.module.bpm.service.oa;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimeCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAOvertimePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAOvertimeDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAOvertimeMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAQuotaLockMapper;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaAttendanceBusinessStartHolder;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_DAY_QUOTA_EXCEEDED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_NOT_EXISTS;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_NOT_SAME_DAY;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_OVERTIME_TOO_SHORT;

/**
 * OA 加班申请 Service 实现类
 */
@Service
@Validated
public class BpmOAOvertimeServiceImpl implements BpmOAOvertimeService {

    /**
     * OA 加班对应的流程定义 KEY
     */
    public static final String PROCESS_KEY = "oa_overtime";

    private static final String QUERY_PERMISSION = "bpm:oa-overtime:query";

    private static final Set<Integer> QUOTA_STATUSES = Set.of(
            BpmTaskStatusEnum.RUNNING.getStatus(),
            BpmTaskStatusEnum.APPROVE.getStatus());

    @Resource
    private BpmOAOvertimeMapper overtimeMapper;

    @Resource
    private BpmOAQuotaLockMapper quotaLockMapper;

    @Resource
    private BpmProcessInstanceApi processInstanceApi;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Resource
    private OaBillAccessPermission oaBillAccessPermission;

    @Resource
    private ObjectProvider<RuntimeService> runtimeServiceProvider;

    @Resource
    private ObjectProvider<HistoryService> historyServiceProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOvertime(Long userId, BpmOAOvertimeCreateReqVO createReqVO) {
        if (StrUtil.isBlank(createReqVO.getReason())) {
            throw invalidParamException("加班事由不能为空");
        }
        if (createReqVO.getStartTime() == null || createReqVO.getEndTime() == null) {
            throw invalidParamException("开始结束时间不能为空");
        }
        if (StrUtil.isBlank(createReqVO.getHoliday())) {
            throw invalidParamException("是否法定节假日不能为空");
        }
        String holiday = createReqVO.getHoliday().trim();
        if (!"true".equals(holiday) && !"false".equals(holiday)) {
            throw invalidParamException("是否法定节假日取值无效");
        }
        LocalDateTime startTime = createReqVO.getStartTime();
        LocalDateTime endTime = createReqVO.getEndTime();
        if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
            throw exception(OA_OVERTIME_NOT_SAME_DAY);
        }
        BigDecimal hours = OaOvertimeHours.calc(startTime, endTime)
                .orElseThrow(() -> exception(OA_OVERTIME_TOO_SHORT));

        LocalDate day = startTime.toLocalDate();
        OaQuotaLocks.lockOvertimeDay(quotaLockMapper, userId, day);
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEndExclusive = day.plusDays(1).atStartOfDay();
        List<BpmOAOvertimeDO> dayRows = overtimeMapper.selectByUserAndDayForUpdate(userId, dayStart, dayEndExclusive);
        BigDecimal occupied = BigDecimal.ZERO;
        for (BpmOAOvertimeDO existing : dayRows) {
            if (!occupiesQuota(existing.getStatus())) {
                continue;
            }
            occupied = occupied.add(existing.getHours() == null ? BigDecimal.ZERO : existing.getHours());
            if (overlaps(startTime, endTime, existing.getStartTime(), existing.getEndTime())) {
                throw invalidParamException("加班时间与已有申请重叠");
            }
        }
        if (occupied.add(hours).compareTo(OaOvertimeHours.MAX_HOURS) > 0) {
            throw exception(OA_OVERTIME_DAY_QUOTA_EXCEEDED);
        }

        BpmOAOvertimeDO overtime = BeanUtils.toBean(createReqVO, BpmOAOvertimeDO.class)
                .setUserId(userId)
                .setHoliday(holiday)
                .setHours(hours)
                .setStatus(BpmTaskStatusEnum.RUNNING.getStatus())
                .setAttendanceSyncStatus(OaAttendanceSyncStatusEnum.NOT_SYNCED.getStatus());
        overtimeMapper.insert(overtime);

        Map<String, Object> processInstanceVariables = new HashMap<>();
        processInstanceVariables.put("hours", hours);
        processInstanceVariables.put("holiday", Boolean.parseBoolean(holiday));
        if (createReqVO.getStartCompanyDeptId() != null) {
            processInstanceVariables.put("startCompanyDeptId", createReqVO.getStartCompanyDeptId());
        }
        String processInstanceId = OaAttendanceBusinessStartHolder.callBusiness(() ->
                processInstanceApi.createProcessInstance(userId,
                        new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY)
                                .setVariables(processInstanceVariables).setBusinessKey(String.valueOf(overtime.getId())))
                        .getCheckedData());

        overtimeMapper.updateById(new BpmOAOvertimeDO().setId(overtime.getId()).setProcessInstanceId(processInstanceId));
        reconcileImmediateTerminal(overtime.getId(), processInstanceId);
        return overtime.getId();
    }

    @Override
    public void updateOvertimeStatus(Long id, Integer status) {
        validateOvertimeExists(id);
        overtimeMapper.updateById(new BpmOAOvertimeDO().setId(id).setStatus(status));
    }

    @Override
    public void updateOvertimeStatus(Long id, Integer status, String processInstanceId) {
        BpmOAOvertimeDO row = overtimeMapper.selectById(id);
        if (row == null || StrUtil.isBlank(processInstanceId)
                || StrUtil.isBlank(row.getProcessInstanceId())
                || !processInstanceId.equals(row.getProcessInstanceId())) {
            return;
        }
        overtimeMapper.updateById(new BpmOAOvertimeDO().setId(id).setStatus(status));
    }

    private void reconcileImmediateTerminal(Long id, String processInstanceId) {
        Integer engineStatus = readEngineStatus(processInstanceId);
        if (engineStatus == null) {
            return;
        }
        if (Objects.equals(engineStatus, BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            return;
        }
        updateOvertimeStatus(id, engineStatus, processInstanceId);
    }

    private Integer readEngineStatus(String processInstanceId) {
        if (StrUtil.isBlank(processInstanceId)) {
            return null;
        }
        RuntimeService runtimeService = runtimeServiceProvider == null ? null : runtimeServiceProvider.getIfAvailable();
        if (runtimeService != null) {
            ProcessInstance running = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .includeProcessVariables()
                    .singleResult();
            if (running != null && running.getProcessVariables() != null) {
                Object raw = running.getProcessVariables().get(
                        cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
                if (raw instanceof Integer integer) {
                    return integer;
                }
            }
        }
        HistoryService historyService = historyServiceProvider == null ? null : historyServiceProvider.getIfAvailable();
        if (historyService == null) {
            return null;
        }
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .includeProcessVariables()
                .singleResult();
        if (historic == null || historic.getProcessVariables() == null) {
            return null;
        }
        Object raw = historic.getProcessVariables().get(
                cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS);
        return raw instanceof Integer integer ? integer : null;
    }

    private void validateOvertimeExists(Long id) {
        if (overtimeMapper.selectById(id) == null) {
            throw exception(OA_OVERTIME_NOT_EXISTS);
        }
    }

    @Override
    public BpmOAOvertimeDO getOvertime(Long id, Long userId) {
        BpmOAOvertimeDO overtime = overtimeMapper.selectById(id);
        if (overtime == null) {
            throw exception(OA_OVERTIME_NOT_EXISTS);
        }
        if (Objects.equals(overtime.getUserId(), userId)
                || securityFrameworkService.hasPermission(QUERY_PERMISSION)
                || oaBillAccessPermission.isActiveTaskCandidateOrAssignee(overtime.getProcessInstanceId(), userId)) {
            return overtime;
        }
        throw exception(OA_OVERTIME_ACCESS_DENIED);
    }

    @Override
    public PageResult<BpmOAOvertimeDO> getOvertimePage(Long userId, BpmOAOvertimePageReqVO pageReqVO) {
        Long filterUserId = securityFrameworkService.hasPermission(QUERY_PERMISSION) ? null : userId;
        return overtimeMapper.selectPage(filterUserId, pageReqVO);
    }

    private static boolean occupiesQuota(Integer status) {
        return status != null && QUOTA_STATUSES.contains(status);
    }

    private static boolean overlaps(LocalDateTime start, LocalDateTime end,
                                    LocalDateTime otherStart, LocalDateTime otherEnd) {
        if (otherStart == null || otherEnd == null) {
            return false;
        }
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

}
