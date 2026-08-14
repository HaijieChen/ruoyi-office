package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaMode;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPolicySnapshot;

import java.util.Set;

/**
 * MFA 控制面：生命周期状态机、类型化策略、版本门闩（ADR-MFA-v2 §4 / ①）。
 */
public interface MfaPolicyControlService {

    /**
     * 从主库强一致解析当前可用策略快照（含生命周期）。
     * 每次用户态签发/refresh 前调用。
     */
    MfaPolicySnapshot resolveEffectivePolicy(Long tenantId);

    /**
     * 仅读取主库门闩上的 policyVersion（强一致）。
     */
    long readPrimaryPolicyVersionGate();

    /**
     * 确认并写入全局策略；若 mode 为非 OFF 且当前 UNINITIALIZED，事务内原子转 ARMED。
     * 返回新的 policyVersion。
     */
    long confirmGlobalPolicy(MfaMode mode, Set<String> allowedFactors);

    /**
     * 确认租户策略。全局 REQUIRED 时租户不得降级。
     */
    long confirmTenantPolicy(Long tenantId, MfaMode mode, Set<String> allowedFactors);

    /**
     * readiness：ARMED 但加载失败则返回 false。
     */
    boolean isReady();

    /**
     * 丢弃本地缓存，强制下次从主库重载。
     */
    void invalidateCache();

}
