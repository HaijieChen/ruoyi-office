package cn.iocoder.yudao.module.bpm.service.oa;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOALeaveCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.oa.vo.BpmOALeavePageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.oa.BpmOALeaveDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.oa.BpmOALeaveMapper;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.security.OaBillAccessPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_LEAVE_ACCESS_DENIED;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.OA_LEAVE_NOT_EXISTS;

/**
 * OA 请假申请 Service 实现类
 *
 * @author jason
 * @author 宇擎源码
 */
@Service
@Validated
public class BpmOALeaveServiceImpl implements BpmOALeaveService {

    /**
     * OA 请假对应的流程定义 KEY
     */
    public static final String PROCESS_KEY = "oa_leave";

    private static final String QUERY_PERMISSION = "bpm:oa-leave:query";

    @Resource
    private BpmOALeaveMapper leaveMapper;

    @Resource
    private BpmProcessInstanceApi processInstanceApi;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @Resource
    private OaBillAccessPermission oaBillAccessPermission;

    @Resource
    private PermissionApi permissionApi;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLeave(Long userId, BpmOALeaveCreateReqVO createReqVO) {
        // 插入 OA 请假单
        long day = Math.max(1, ChronoUnit.DAYS.between(
                createReqVO.getStartTime().toLocalDate(), createReqVO.getEndTime().toLocalDate()) + 1);
        BpmOALeaveDO leave = BeanUtils.toBean(createReqVO, BpmOALeaveDO.class)
                .setUserId(userId).setDay(day).setStatus(BpmTaskStatusEnum.RUNNING.getStatus());
        leaveMapper.insert(leave);

        // 发起 BPM 流程
        Map<String, Object> processInstanceVariables = new HashMap<>();
        processInstanceVariables.put("day", day);
        processInstanceVariables.put("billCode", "LEAVE-" + leave.getId());
        if (createReqVO.getStartCompanyDeptId() != null) {
            processInstanceVariables.put("startCompanyDeptId", createReqVO.getStartCompanyDeptId());
        }
        if (createReqVO.getStartDeptId() != null) {
            processInstanceVariables.put("startDeptId", createReqVO.getStartDeptId());
        }
        String processInstanceId = processInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY)
                        .setVariables(processInstanceVariables).setBusinessKey(String.valueOf(leave.getId()))
                        .setStartUserSelectAssignees(createReqVO.getStartUserSelectAssignees())).getCheckedData();

        // 将工作流的编号，更新到 OA 请假单中
        leaveMapper.updateById(new BpmOALeaveDO().setId(leave.getId()).setProcessInstanceId(processInstanceId));
        return leave.getId();
    }

    @Override
    public void updateLeaveStatus(Long id, Integer status) {
        validateLeaveExists(id);
        leaveMapper.updateById(new BpmOALeaveDO().setId(id).setStatus(status));
    }

    private void validateLeaveExists(Long id) {
        if (leaveMapper.selectById(id) == null) {
            throw exception(OA_LEAVE_NOT_EXISTS);
        }
    }

    @Override
    public BpmOALeaveDO getLeave(Long id, Long userId) {
        BpmOALeaveDO leave = leaveMapper.selectById(id);
        if (leave == null) {
            throw exception(OA_LEAVE_NOT_EXISTS);
        }
        if (securityFrameworkService.hasPermission(QUERY_PERMISSION)
                || oaBillAccessPermission.canReadOaBill(userId, leave.getUserId(), leave.getProcessInstanceId())) {
            return leave;
        }
        throw exception(OA_LEAVE_ACCESS_DENIED);
    }

    @Override
    public PageResult<BpmOALeaveDO> getLeavePage(Long userId, BpmOALeavePageReqVO pageReqVO) {
        return leaveMapper.selectPage(userId, pageReqVO);
    }

    @Override
    public PageResult<BpmOALeaveDO> getLeaveReportPage(Long userId, BpmOALeavePageReqVO pageReqVO) {
        java.util.Objects.requireNonNull(userId, "login user required");
        DeptDataPermissionRespDTO scope = permissionApi.getDeptDataPermission(userId).getCheckedData();
        if (scope != null && Boolean.TRUE.equals(scope.getAll())) {
            return leaveMapper.selectPageByUsers(null, pageReqVO);
        }
        Set<Long> userIds = new HashSet<>();
        if (scope != null) {
            if (Boolean.TRUE.equals(scope.getSelf())) {
                userIds.add(userId);
            }
            if (scope.getDeptIds() != null && !scope.getDeptIds().isEmpty()) {
                adminUserApi.getUserListByDeptIds(scope.getDeptIds()).getCheckedData()
                        .forEach(user -> userIds.add(user.getId()));
            }
        }
        return leaveMapper.selectPageByUsers(userIds, pageReqVO);
    }

}
