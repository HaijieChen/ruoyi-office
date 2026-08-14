package cn.iocoder.yudao.module.system.service.mfa;

import org.springframework.stereotype.Service;

/**
 * 切片 3：基于因子权威探测用户 MFA 就绪状态。
 */
@Service
public class MfaUserFactorProbeImpl implements MfaUserFactorProbe {

    private final MfaFactorService factorService;

    public MfaUserFactorProbeImpl(MfaFactorService factorService) {
        this.factorService = factorService;
    }

    @Override
    public boolean isUserMfaEnabled(Long tenantId, Long userId) {
        // OPTIONAL 用户开关完整实现前：有 ACTIVE 因子即视为启用
        return hasEligibleActiveFactor(tenantId, userId);
    }

    @Override
    public boolean hasEligibleActiveFactor(Long tenantId, Long userId) {
        return factorService.hasActiveFactor(tenantId, userId);
    }

    @Override
    public boolean isEnrollmentComplete(Long tenantId, Long userId) {
        return factorService.hasActiveFactor(tenantId, userId);
    }

    @Override
    public Long getSessionPolicyVersion(String refreshToken) {
        return null; // refresh family 落盘后由 Token 元数据/会话表提供
    }

    @Override
    public boolean isEnrollmentPendingOnSession(String refreshToken) {
        return false;
    }

    @Override
    public boolean isRecoveryPendingOnSession(String refreshToken) {
        return false;
    }
}
