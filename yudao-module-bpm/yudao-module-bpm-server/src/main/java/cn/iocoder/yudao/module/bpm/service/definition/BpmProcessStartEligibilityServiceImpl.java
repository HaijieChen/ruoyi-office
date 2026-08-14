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
        return evaluate(processKey, false);
    }

    @Override
    public BpmProcessStartEligibility evaluate(String processKey, boolean trustedBusinessStart) {
        // EXP-87 G1：薪税 key — 通用通道 hide+deny；可信业务通道走正常业务权限
        if (isMenuOnlyPaymentProcess(processKey) && !trustedBusinessStart) {
            String required = BpmEmbedProcessStartPermissionRegistry.requiredPermission(processKey);
            String denyMsg = StrUtil.blankToDefault(
                    BpmEmbedProcessStartPermissionRegistry.denyMessage(processKey),
                    "该流程仅允许从业务菜单发起，禁止通用流程发起");
            // 无权限：隐藏+拒绝；有权限：仍拒绝通用直启（须走独立菜单 API）
            return BpmProcessStartEligibility.denied(required, denyMsg);
        }
        if (!BpmEmbedProcessStartPermissionRegistry.isEmbedProcess(processKey)) {
            return BpmProcessStartEligibility.allowed();
        }
        String required = BpmEmbedProcessStartPermissionRegistry.requiredPermission(processKey);
        if (StrUtil.isBlank(required)) {
            return BpmProcessStartEligibility.denied(null, MISSING_CONFIG_REASON);
        }
        // 可信通道下薪税：有权限则允许；无权限仍拒绝（非 fail-open）
        String denyMsg = StrUtil.blankToDefault(
                BpmEmbedProcessStartPermissionRegistry.denyMessage(processKey),
                MISSING_CONFIG_REASON);
        if (!securityFrameworkService.hasPermission(required)) {
            // 可信通道缺权：用更明确的缺权文案，避免「请从菜单发起」误导
            if (trustedBusinessStart && isMenuOnlyPaymentProcess(processKey)) {
                denyMsg = "无" + ("finance_salary_payment_apply".equals(processKey.trim()) ? "薪资" : "税金")
                        + "付款发起权限，请联系管理员分配对应角色";
            }
            return BpmProcessStartEligibility.denied(required, denyMsg);
        }
        return BpmProcessStartEligibility.builder()
                .canStart(true)
                .requiredStartPermission(required)
                .build();
    }

    @Override
    public boolean shouldHideFromStartList(String processKey) {
        // 薪税始终从通用目录隐藏（与 trusted 无关）
        if (isMenuOnlyPaymentProcess(processKey)) {
            return true;
        }
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
        BpmProcessStartEligibility e = evaluate(processKey, trustedBusinessStart);
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
