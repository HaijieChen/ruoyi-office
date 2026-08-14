package cn.iocoder.yudao.module.system.service.mfa;

import cn.iocoder.yudao.module.system.service.mfa.model.MfaFactorView;
import cn.iocoder.yudao.module.system.service.mfa.model.MfaPendingTotp;

import java.util.List;

/**
 * 因子通道（切片 2/3）：挑战发送、校验、TOTP 绑定。
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
     * 首次正确 TOTP 后 PENDING→ACTIVE。
     */
    boolean activatePendingTotp(Long tenantId, Long userId, String factorId, String code);

    void clear();
}
