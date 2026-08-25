package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaControlTuple;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;

import java.util.Set;

/**
 * Policy Authority 只读 + 策略确认写（ADR-MFA-v3 切片 1）。
 */
public interface MfaPolicyAuthority {

    /**
     * 主库强一致 control tuple（lifecycle + epochs + checksum）。
     */
    MfaControlTuple readControlTuple();

    /**
     * 解析有效策略（含租户层级规则 §4.1）。
     */
    MfaPolicySnapshot resolveEffectivePolicy(Long tenantId);

    /**
     * 确认全局策略；首个非 OFF 同事务 ARMED。
     * @return new globalPolicyEpoch
     */
    long confirmGlobalPolicy(MfaMode mode, Set<String> allowedFactors);

    /**
     * 确认租户策略。
     * @return new tenant policyEpoch（并推进 global epoch 保持门闩一致）
     */
    long confirmTenantPolicy(Long tenantId, MfaMode mode, Set<String> allowedFactors);

    boolean isReady();

    void invalidateCache();
}
