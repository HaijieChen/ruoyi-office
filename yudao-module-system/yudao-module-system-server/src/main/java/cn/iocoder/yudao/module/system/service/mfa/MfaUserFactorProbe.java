package cn.iocoder.yudao.module.system.service.mfa;

/**
 * 用户因子/绑定探测（③ 完整实现前可 stub）。
 * <p>
 * 默认：无因子、未启用 MFA、enrollment 未完成。
 */
public interface MfaUserFactorProbe {

    /**
     * 用户是否在 OPTIONAL 下主动启用了 MFA。
     */
    boolean isUserMfaEnabled(Long tenantId, Long userId);

    /**
     * 是否存在至少一个合格 ACTIVE 因子（非仅 EMAIL 的高权限规则在 ③ 细化）。
     */
    boolean hasEligibleActiveFactor(Long tenantId, Long userId);

    /**
     * enrollment 是否完成。
     */
    boolean isEnrollmentComplete(Long tenantId, Long userId);

    /**
     * 会话/ refresh 快照上的 policyVersion（无快照时返回 null）。
     */
    Long getSessionPolicyVersion(String refreshToken);

    /**
     * 会话是否 enrollment pending / recovery pending。
     */
    boolean isEnrollmentPendingOnSession(String refreshToken);

    boolean isRecoveryPendingOnSession(String refreshToken);

    /**
     * 默认 stub：全部未启用、无因子。
     */
    class Noop implements MfaUserFactorProbe {
        @Override
        public boolean isUserMfaEnabled(Long tenantId, Long userId) {
            return false;
        }

        @Override
        public boolean hasEligibleActiveFactor(Long tenantId, Long userId) {
            return false;
        }

        @Override
        public boolean isEnrollmentComplete(Long tenantId, Long userId) {
            return true; // OFF 默认路径视为无 enrollment 缺口
        }

        @Override
        public Long getSessionPolicyVersion(String refreshToken) {
            return null;
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

}
