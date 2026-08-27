package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.other;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.api.task.BpmJobPositionApproverProvider;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.dept.BpmStartCompanyDeptSupport;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 勾选职务字典，只取发起任职公司下该职务的员工账号。
 */
@Component
public class BpmTaskCandidateStartUserCompanyJobPositionStrategy implements BpmTaskCandidateStrategy {

    @Resource
    private DeptApi deptApi;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    @Lazy
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private ObjectProvider<BpmJobPositionApproverProvider> approverProvider;

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.START_USER_COMPANY_JOB_POSITION;
    }

    @Override
    public void validateParam(String param) {
        if (StrUtil.isBlank(param)) {
            throw new IllegalArgumentException("请选择职务");
        }
    }

    @Override
    public Set<Long> calculateUsersByTask(DelegateExecution execution, String param) {
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        Long startUserId = NumberUtils.parseLong(processInstance.getStartUserId());
        return resolve(param, startUserId, execution.getVariables());
    }

    @Override
    public Set<Long> calculateUsersByActivity(BpmnModel bpmnModel, String activityId, String param,
                                              Long startUserId, String processDefinitionId,
                                              Map<String, Object> processVariables) {
        return resolve(param, startUserId, processVariables);
    }

    Set<Long> resolve(String param, Long startUserId, Map<String, Object> processVariables) {
        BpmJobPositionApproverProvider provider = approverProvider.getIfAvailable();
        if (provider == null) {
            return new HashSet<>();
        }
        Long companyId = BpmStartCompanyDeptSupport.resolveStartCompanyDeptId(
                processVariables, startUserId, deptApi, adminUserApi);
        Set<String> positions = Arrays.stream(StrUtil.blankToDefault(param, "").split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        return provider.listUserIdsByJobPositions(positions, companyId);
    }
}
