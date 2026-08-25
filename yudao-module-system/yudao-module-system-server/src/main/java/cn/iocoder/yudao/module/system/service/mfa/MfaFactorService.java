package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorView;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingEmail;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingTotp;

import java.util.List;

/**
 * 因子通道（切片 2/4）：挑战发送、校验、TOTP 绑定。
 * <p>
 * 不负责策略决策；不默认启用 OPTIONAL/REQUIRED。
 */
public interface MfaFactorService {

    void sendChallengeCode(String rawFlowToken, String factorId, String factorType);

    boolean verifyChallengeCode(String rawFlowToken, String factorId, String factorType, String code);

    /** 测试/迁移：直接注册 ACTIVE 因子 */
    void registerActiveFactor(Long tenantId, Long userId, String factorId, String factorType,
                              String secretOrDestination);

    List<MfaFactorView> listActiveFactors(Long tenantId, Long userId);

    boolean hasActiveFactor(Long tenantId, Long userId);

    String resolveFactorType(Long tenantId, Long userId, String factorId);

    /**
     * ENROLLMENT：创建 PENDING TOTP，返回 secret（仅一次）。
     */
    MfaPendingTotp startPendingTotp(Long tenantId, Long userId, String accountName);

    /**
     * 校验 PENDING TOTP，不改变 status / last_used_step。失败返回 null。
     */
    Long matchPendingTotpStep(Long tenantId, Long userId, String factorId, String code);

    /**
     * PENDING→ACTIVE CAS；可选写入 lastUsedStep。
     */
    boolean tryActivatePendingFactor(Long tenantId, Long userId, String factorId, Long lastUsedStep);

    /**
     * ACTIVE→PENDING 补偿（Token / complete 失败回滚）。
     */
    boolean revertFactorToPending(Long tenantId, Long userId, String factorId);

    /**
     * 兼容：match + activate（不可用于 Token 签发前）。
     */
    boolean activatePendingTotp(Long tenantId, Long userId, String factorId, String code);

    MfaPendingEmail startPendingEmail(Long tenantId, Long userId, String email);

    boolean activatePendingEmail(Long tenantId, Long userId, String factorId, String code);

    boolean hasActiveFactorOfType(Long tenantId, Long userId, String factorType);

    String peekFactorStatus(Long tenantId, Long userId, String factorId);

    /** ACTIVE 因子解绑（仅本人调用方校验）。 */
    boolean revokeActiveFactor(Long tenantId, Long userId, String factorId);

    void clear();
}
