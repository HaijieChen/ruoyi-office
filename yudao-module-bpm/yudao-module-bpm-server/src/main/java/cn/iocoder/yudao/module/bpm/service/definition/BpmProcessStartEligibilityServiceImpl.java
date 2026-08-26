package cn.iocoder.yudao.module.bpm.service.definition;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.MODEL_DEPLOY_FAIL_START_PERMISSION_NOT_CONFIG;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_START_PERMISSION_DENIED;

/**
 * 嵌入式流程发起资格：列表隐藏 / 详情预检 / 启动校验。
 * <p>
 * EXP-87 验收：薪资/税金可出现在<strong>统一发起目录</strong>（有 create 权限时），
 * 但通用 {@code createProcessInstance} <strong>仍拒绝直启</strong>；业务 API 可信通道可发。
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
        return evaluate(processKey, false);
    }

    @Override
    public BpmProcessStartEligibility evaluate(String processKey, boolean trustedBusinessStart) {
        // 谁能发起只看流程设计（目录/canUserStart）；业务菜单权限不再作为发起资格
        return BpmProcessStartEligibility.allowed();
    }

    @Override
    public boolean shouldHideFromStartList(String processKey) {
        // 与其它嵌入式一致：无 create 权限才隐藏；有权限则在统一目录露出
        return !evaluate(processKey).isCanStart();
    }

    private static boolean isMenuOnlyPaymentProcess(String processKey) {
        if (StrUtil.isBlank(processKey)) {
            return false;
        }
        String key = processKey.trim();
        return "finance_salary_payment_apply".equals(key) || "finance_tax_payment_apply".equals(key);
    }

    @Override
    public void validateStartOrThrow(String processKey) {
        validateStartOrThrow(processKey, false);
    }

    @Override
    public void validateStartOrThrow(String processKey, boolean trustedBusinessStart) {
        // 薪税仍禁止通用 createProcessInstance 直启，须走业务 API；不再校验菜单 create 权限
        if (isMenuOnlyPaymentProcess(processKey) && !trustedBusinessStart) {
            String denyMsg = StrUtil.blankToDefault(
                    BpmEmbedProcessStartPermissionRegistry.denyMessage(processKey),
                    "该流程仅允许从业务入口发起，禁止通用流程直启");
            throw exception(PROCESS_INSTANCE_START_PERMISSION_DENIED, denyMsg);
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
