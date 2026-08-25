package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;

import java.util.Set;

/**
 * 兼容门面：委托 {@link MfaPolicyAuthority}（ADR-MFA-v3）。
 * 切片 2 起新代码请直接依赖 PolicyAuthority。
 */
public interface MfaPolicyControlService {

    MfaPolicySnapshot resolveEffectivePolicy(Long tenantId);

    long readPrimaryPolicyVersionGate();

    long confirmGlobalPolicy(MfaMode mode, Set<String> allowedFactors);

    long confirmTenantPolicy(Long tenantId, MfaMode mode, Set<String> allowedFactors);

    boolean isReady();

    void invalidateCache();
}
