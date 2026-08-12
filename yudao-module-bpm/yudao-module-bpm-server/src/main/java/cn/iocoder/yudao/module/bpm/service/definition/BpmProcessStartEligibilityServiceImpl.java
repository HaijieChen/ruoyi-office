package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.MODEL_DEPLOY_FAIL_START_PERMISSION_NOT_CONFIG;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED;

/**
 * 嵌入式流程发起资格：列表隐藏 / 详情预检 / 启动校验共用同一评估逻辑。
 */
@Service
public class BpmProcessStartEligibilityServiceImpl implements BpmProcessStartEligibilityService {

    private static final String MISSING_CONFIG_REASON = "流程未配置发起权限，禁止发起，请联系管理员";

    private final SecurityFrameworkService securityFrameworkService;

    public BpmProcessStartEligibilityServiceImpl(SecurityFrameworkService securityFrameworkService) {
        this.securityFrameworkService = securityFrameworkService;
    }

    @Override
    public BpmProcessStartEligibility evaluate(String processKey) {
        if (!BpmEmbedProcessStartPermissionRegistry.isEmbedProcess(processKey)) {
            return BpmProcessStartEligibility.allowed();
        }
        String required = BpmEmbedProcessStartPermissionRegistry.requiredPermission(processKey);
        if (StrUtil.isBlank(required)) {
            return BpmProcessStartEligibility.denied(null, MISSING_CONFIG_REASON);
        }
        String denyMsg = StrUtil.blankToDefault(
                BpmEmbedProcessStartPermissionRegistry.denyMessage(processKey),
                MISSING_CONFIG_REASON);
        if (!securityFrameworkService.hasPermission(required)) {
            return BpmProcessStartEligibility.denied(required, denyMsg);
        }
        return BpmProcessStartEligibility.builder()
                .canStart(true)
                .requiredStartPermission(required)
                .build();
    }

    @Override
    public boolean shouldHideFromStartList(String processKey) {
        return !evaluate(processKey).isCanStart();
    }

    @Override
    public void validateStartOrThrow(String processKey) {
        BpmProcessStartEligibility e = evaluate(processKey);
        if (!e.isCanStart()) {
            String reason = StrUtil.blankToDefault(e.getCannotStartReason(), MISSING_CONFIG_REASON);
            throw exception(PROCESS_INSTANCE_START_PERMISSION_DENIED, reason);
        }
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
