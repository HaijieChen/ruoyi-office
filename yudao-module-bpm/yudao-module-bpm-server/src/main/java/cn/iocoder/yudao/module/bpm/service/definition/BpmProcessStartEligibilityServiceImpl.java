package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.MODEL_DEPLOY_FAIL_START_PERMISSION_NOT_CONFIG;

/**
 * 发起资格：目录可见与可发起以流程模型配置为准（是否可见 + 谁可以发起）。
 * 不再用业务 create 权限显隐；薪税与其它流程一样允许统一发起直启。
 */
@Service
public class BpmProcessStartEligibilityServiceImpl implements BpmProcessStartEligibilityService {

    private final SecurityFrameworkService securityFrameworkService;

    public BpmProcessStartEligibilityServiceImpl(SecurityFrameworkService securityFrameworkService) {
        this.securityFrameworkService = securityFrameworkService;
    }

    @Override
    public BpmProcessStartEligibility evaluate(String processKey) {
        return evaluate(processKey, false);
    }

    @Override
    public BpmProcessStartEligibility evaluate(String processKey, boolean trustedBusinessStart) {
        return BpmProcessStartEligibility.allowed();
    }

    @Override
    public boolean shouldHideFromStartList(String processKey) {
        return false;
    }

    @Override
    public void validateStartOrThrow(String processKey) {
        validateStartOrThrow(processKey, false);
    }

    @Override
    public void validateStartOrThrow(String processKey, boolean trustedBusinessStart) {
        // 可见即可发起：人员范围由流程模型 canUserStartProcessDefinition 校验
    }

    @Override
    public void validateEmbedStartPermissionConfigured(String processKey) {
        if (!BpmEmbedProcessStartPermissionRegistry.isEmbedProcess(processKey)) {
            return;
        }
        String required = BpmEmbedProcessStartPermissionRegistry.requiredPermission(processKey);
        if (StrUtil.isBlank(required)) {
            throw exception(MODEL_DEPLOY_FAIL_START_PERMISSION_NOT_CONFIG, processKey);
        }
    }
}
