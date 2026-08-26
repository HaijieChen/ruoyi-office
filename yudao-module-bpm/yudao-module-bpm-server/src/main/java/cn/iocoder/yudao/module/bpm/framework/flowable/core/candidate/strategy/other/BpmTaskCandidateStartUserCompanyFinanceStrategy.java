package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.other;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmCompanyFinanceApproverProvider;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 发起人所属公司的财务审批人。不改组织树，只查财务映射。
 */
@Component
public class BpmTaskCandidateStartUserCompanyFinanceStrategy implements BpmTaskCandidateStrategy {

    private static final String ORG_TYPE_COMPANY = "1";

    @Resource
    @Lazy
    BpmProcessInstanceService processInstanceService;
    @Resource
    AdminUserApi adminUserApi;
    @Resource
    DeptApi deptApi;
    @Resource
    ObjectProvider<BpmCompanyFinanceApproverProvider> approverProvider;

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.START_USER_COMPANY_FINANCE;
    }

    @Override
    public void validateParam(String param) {
        // 无需参数
    }

    @Override
    public boolean isParamRequired() {
        return false;
    }

    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        Long startUserId = NumberUtils.parseLong(processInstance.getStartUserId());
        return resolveApprovers(startUserId);
    }

    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId,
                                              Map<String, Object> processVariables) {
        return resolveApprovers(startUserId);
    }

    Set<Long> resolveApprovers(Long startUserId) {
        BpmCompanyFinanceApproverProvider provider = approverProvider.getIfAvailable();
        if (provider == null) {
            return new HashSet<>();
        }
        Long companyDeptId = resolveStartUserCompanyDeptId(startUserId);
        if (companyDeptId == null) {
            return new HashSet<>();
        }
        return provider.listUserIdsByCompanyDeptId(companyDeptId);
    }

    Long resolveStartUserCompanyDeptId(Long startUserId) {
        if (startUserId == null) {
            return null;
        }
        AdminUserRespDTO user = adminUserApi.getUser(startUserId).getCheckedData();
        if (user == null || user.getDeptId() == null) {
            return null;
        }
        Long deptId = user.getDeptId();
        for (int i = 0; i < 16 && deptId != null && deptId > 0; i++) {
            DeptRespDTO dept = deptApi.getDept(deptId).getCheckedData();
            if (dept == null) {
                return null;
            }
            if (StrUtil.equals(ORG_TYPE_COMPANY, dept.getOrgType())) {
                return dept.getId();
            }
            Long parentId = dept.getParentId();
            if (parentId == null || parentId.equals(dept.getId())) {
                return null;
            }
            deptId = parentId;
        }
        return null;
    }
}
