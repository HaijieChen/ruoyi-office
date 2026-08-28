package cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.strategy.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色 {@link BpmTaskCandidateStrategy} 实现类。
 *
 * <p>param 支持：
 * <ul>
 *   <li>角色编号：{@code 1,2,3}</li>
 *   <li>角色编码：{@code contract_seal_admin,contract_mail}（CS-F12，环境无关种子）</li>
 *   <li>混排：{@code 1,contract_seal_admin}</li>
 * </ul>
 */
@Component
public class BpmTaskCandidateRoleStrategy implements BpmTaskCandidateStrategy {

    @Resource
    private RoleApi roleApi;
    @Resource
    private PermissionApi permissionApi;

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.ROLE;
    }

    @Override
    public void validateParam(String param) {
        Set<Long> roleIds = resolveRoleIds(param);
        if (CollUtil.isEmpty(roleIds)) {
            throw exception(MODEL_DEPLOY_FAIL_TASK_CANDIDATE_NOT_CONFIG, "角色 " + param);
        }
        roleApi.validRoleList(roleIds);
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        Set<Long> roleIds = resolveRoleIds(param);
        if (CollUtil.isEmpty(roleIds)) {
            return Set.of();
        }
        return permissionApi.getUserRoleIdListByRoleIds(roleIds).getCheckedData();
    }

    /**
     * 解析 param 中的角色 id 与 code。
     */
    Set<Long> resolveRoleIds(String param) {
        Set<Long> roleIds = new HashSet<>();
        if (StrUtil.isBlank(param)) {
            return roleIds;
        }
        List<String> tokens = StrUtil.splitTrim(param, ',');
        List<String> codes = new ArrayList<>();
        for (String t : tokens) {
            if (StrUtil.isBlank(t)) {
                continue;
            }
            if (t.chars().allMatch(Character::isDigit)) {
                roleIds.add(Long.parseLong(t));
            } else {
                codes.add(t);
            }
        }
        if (CollUtil.isNotEmpty(codes)) {
            List<Long> byCodes = roleApi.getRoleIdListByCodes(codes).getCheckedData();
            if (CollUtil.isNotEmpty(byCodes)) {
                roleIds.addAll(byCodes);
            }
        }
        return roleIds;
    }

}
