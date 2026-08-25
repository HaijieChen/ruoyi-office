package cn.iocoder.yudao.module.system.service.mfa.support;

import cn.iocoder.yudao.module.system.service.mfa.model.IssuanceDecision;

/**
 * 底层 Token 服务门闩：仅 Facade 在持有有效 {@link IssuanceDecision} 时放行 ADMIN 用户态签发。
 */
public final class MfaIssuanceGuard {

    private static final ThreadLocal<IssuanceDecision> HOLDER = new ThreadLocal<>();

    private MfaIssuanceGuard() {
    }

    public static void bind(IssuanceDecision decision) {
        HOLDER.set(decision);
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static IssuanceDecision current() {
        return HOLDER.get();
    }

    /**
     * 消费并校验当前线程上的决策是否匹配目标主体。
     */
    public static boolean permitAdminIssuance(Long userId, Integer userType) {
        IssuanceDecision decision = HOLDER.get();
        if (decision == null) {
            return false;
        }
        if (decision.getSubjectId() == null || !decision.getSubjectId().equals(userId)) {
            return false;
        }
        if (!decision.isMfaSatisfied() || !decision.isEnrollmentComplete() || decision.isRecoveryRequired()) {
            return false;
        }
        return decision.tryConsume();
    }

}
