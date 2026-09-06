package cn.iocoder.yudao.module.bpm.service.oa;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOAPunchCorrectionPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOAPunchCorrectionDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAPunchCorrectionMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOAQuotaLockMapper;
import cn.iocoder.yudao.module.bpm.enums.OaAttendanceSyncStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaAttendanceBusinessStartHolder;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_PUNCH_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_PUNCH_MONTH_QUOTA_EXCEEDED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_PUNCH_NOT_EXISTS;

/**
 * OA 补卡申请 Service 实现类
 */
@Service
@Validated
public class BpmOAPunchCorrectionServiceImpl implements BpmOAPunchCorrectionService {

    /**
     * OA 补卡对应的流程定义 KEY
     */
    public static final String PROCESS_KEY = "oa_punch_correction";

    /**
     * 每人每月最多补卡次数（按补卡日期所在月）
     */
    public static final int MONTH_QUOTA = 2;

    private static final String QUERY_PERMISSION = "bpm:oa-punch-correction:query";

    private static final Set<Integer> QUOTA_STATUSES = Set.of(
            BpmTaskStatusEnum.RUNNING.getStatus(),
            BpmTaskStatusEnum.APPROVE.getStatus());

    @Resource
    private BpmOAPunchCorrectionMapper punchCorrectionMapper;

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
    public Long createPunchCorrection(Long userId, BpmOAPunchCorrectionCreateReqVO createReqVO) {
        if (StrUtil.isBlank(createReqVO.getReason())) {
            throw invalidParamException("补卡事由不能为空");
        }
        if (createReqVO.getPunchDate() == null) {
            throw invalidParamException("补卡日期不能为空");
        }
        if (createReqVO.getPunchTime() == null) {
            throw invalidParamException("补卡时间不能为空");
        }

        LocalDate punchDate = createReqVO.getPunchDate();
        OaQuotaLocks.lockPunchMonth(quotaLockMapper, userId, punchDate);
        LocalDate monthStart = punchDate.withDayOfMonth(1);
        LocalDate monthEndExclusive = monthStart.plusMonths(1);
        List<BpmOAPunchCorrectionDO> monthRows =
                punchCorrectionMapper.selectByUserAndMonthForUpdate(userId, monthStart, monthEndExclusive);
        int occupied = countOccupied(monthRows);
        if (occupied >= MONTH_QUOTA) {
            throw exception(OA_PUNCH_MONTH_QUOTA_EXCEEDED);
        }

        BpmOAPunchCorrectionDO punch = BeanUtils.toBean(createReqVO, BpmOAPunchCorrectionDO.class)
                .setUserId(userId)
                .setStatus(BpmTaskStatusEnum.RUNNING.getStatus())
                .setAttendanceSyncStatus(OaAttendanceSyncStatusEnum.NOT_SYNCED.getStatus());
        punchCorrectionMapper.insert(punch);

        Map<String, Object> processInstanceVariables = new HashMap<>();
        processInstanceVariables.put("punchDate", punchDate.toString());
        if (createReqVO.getStartCompanyDeptId() != null) {
            processInstanceVariables.put("startCompanyDeptId", createReqVO.getStartCompanyDeptId());
        }
        String processInstanceId = OaAttendanceBusinessStartHolder.callBusiness(() ->
                processInstanceApi.createProcessInstance(userId,
                        new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY)
                                .setVariables(processInstanceVariables).setBusinessKey(String.valueOf(punch.getId())))
                        .getCheckedData());

        punchCorrectionMapper.updateById(new BpmOAPunchCorrectionDO().setId(punch.getId())
                .setProcessInstanceId(processInstanceId));
        reconcileImmediateTerminal(punch.getId(), processInstanceId);
        return punch.getId();
    }

    @Override
    public void updatePunchCorrectionStatus(Long id, Integer status) {
        validatePunchCorrectionExists(id);
        punchCorrectionMapper.updateById(new BpmOAPunchCorrectionDO().setId(id).setStatus(status));
    }

    @Override
    public void updatePunchCorrectionStatus(Long id, Integer status, String processInstanceId) {
        BpmOAPunchCorrectionDO row = punchCorrectionMapper.selectById(id);
        if (row == null || StrUtil.isBlank(processInstanceId)
                || StrUtil.isBlank(row.getProcessInstanceId())
                || !processInstanceId.equals(row.getProcessInstanceId())) {
            return;
        }
        punchCorrectionMapper.updateById(new BpmOAPunchCorrectionDO().setId(id).setStatus(status));
    }

    private void reconcileImmediateTerminal(Long id, String processInstanceId) {
        Integer engineStatus = readEngineStatus(processInstanceId);
        if (engineStatus == null
                || Objects.equals(engineStatus, BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            return;
        }
        updatePunchCorrectionStatus(id, engineStatus, processInstanceId);
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

    private void validatePunchCorrectionExists(Long id) {
        if (punchCorrectionMapper.selectById(id) == null) {
            throw exception(OA_PUNCH_NOT_EXISTS);
        }
    }

    @Override
    public BpmOAPunchCorrectionDO getPunchCorrection(Long id, Long userId) {
        BpmOAPunchCorrectionDO punch = punchCorrectionMapper.selectById(id);
        if (punch == null) {
            throw exception(OA_PUNCH_NOT_EXISTS);
        }
        if (Objects.equals(punch.getUserId(), userId)
                || securityFrameworkService.hasPermission(QUERY_PERMISSION)
                || oaBillAccessPermission.isActiveTaskCandidateOrAssignee(punch.getProcessInstanceId(), userId)) {
            return punch;
        }
        throw exception(OA_PUNCH_ACCESS_DENIED);
    }

    @Override
    public PageResult<BpmOAPunchCorrectionDO> getPunchCorrectionPage(Long userId,
                                                                     BpmOAPunchCorrectionPageReqVO pageReqVO) {
        Long filterUserId = securityFrameworkService.hasPermission(QUERY_PERMISSION) ? null : userId;
        return punchCorrectionMapper.selectPage(filterUserId, pageReqVO);
    }

    @Override
    public Integer getRemainingCount(Long userId, LocalDate punchDate) {
        if (punchDate == null) {
            throw invalidParamException("补卡日期不能为空");
        }
        LocalDate monthStart = punchDate.withDayOfMonth(1);
        LocalDate monthEndExclusive = monthStart.plusMonths(1);
        List<BpmOAPunchCorrectionDO> monthRows =
                punchCorrectionMapper.selectByUserAndMonth(userId, monthStart, monthEndExclusive);
        return Math.max(0, MONTH_QUOTA - countOccupied(monthRows));
    }

    private static int countOccupied(List<BpmOAPunchCorrectionDO> rows) {
        int occupied = 0;
        for (BpmOAPunchCorrectionDO existing : rows) {
            if (occupiesQuota(existing.getStatus())) {
                occupied++;
            }
        }
        return occupied;
    }

    private static boolean occupiesQuota(Integer status) {
        return status != null && QUOTA_STATUSES.contains(status);
    }

}
