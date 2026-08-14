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
        // 薪税：目录可见性按业务 create 权限；通用直启仍由 validateStartOrThrow 硬拒绝
        if (isMenuOnlyPaymentProcess(processKey)) {
            return evaluateMenuOnlyPayment(processKey, trustedBusinessStart);
        }
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

    /**
     * 薪税：有 create → canStart=true（统一目录卡片可见 / 可跳转业务入口）；
     * 无 create → 隐藏+拒绝。
     * 可信通道与通用通道在<strong>权限评估</strong>上一致；通用 BPM 直启另由
     * {@link #validateStartOrThrow(String, boolean)} 硬拒绝。
     */
    private BpmProcessStartEligibility evaluateMenuOnlyPayment(String processKey, boolean trustedBusinessStart) {
        String required = BpmEmbedProcessStartPermissionRegistry.requiredPermission(processKey);
        if (StrUtil.isBlank(required)) {
            return BpmProcessStartEligibility.denied(null, MISSING_CONFIG_REASON);
        }
        if (!securityFrameworkService.hasPermission(required)) {
            String denyMsg;
            if (trustedBusinessStart) {
                denyMsg = "无" + ("finance_salary_payment_apply".equals(processKey.trim()) ? "薪资" : "税金")
                        + "付款发起权限，请联系管理员分配对应角色";
            } else {
                denyMsg = StrUtil.blankToDefault(
                        BpmEmbedProcessStartPermissionRegistry.denyMessage(processKey),
                        MISSING_CONFIG_REASON);
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
        // 目录可见 ≠ 通用直启：非可信通道对薪税硬拒绝 createProcessInstance
        if (isMenuOnlyPaymentProcess(processKey) && !trustedBusinessStart) {
            String required = BpmEmbedProcessStartPermissionRegistry.requiredPermission(processKey);
            String denyMsg = StrUtil.blankToDefault(
                    BpmEmbedProcessStartPermissionRegistry.denyMessage(processKey),
                    "该流程仅允许从业务入口发起，禁止通用流程直启");
            throw exception(PROCESS_INSTANCE_START_PERMISSION_DENIED, denyMsg);
        }
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
