package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * ADR-MFA-v3 tokenClass 契约。
 * <p>
 * 仅 {@link #ACCESS} 可作为业务 Bearer；flow 类见 {@link MfaFlowTokenClass}。
 * 禁止输出/接受 challengeToken、preAuthToken、enrollmentToken、recoveryToken 别名。
 */
public enum MfaTokenClass {

    ACCESS,
    PRE_AUTH,
    ENROLLMENT,
    RECOVERY;

    public boolean isBusinessBearer() {
        return this == ACCESS;
    }

    public static boolean allowsBusinessApi(MfaTokenClass tokenClass) {
        return tokenClass != null && tokenClass.isBusinessBearer();
    }

    public static boolean isFlowClass(MfaTokenClass tokenClass) {
        return tokenClass == PRE_AUTH || tokenClass == ENROLLMENT || tokenClass == RECOVERY;
    }

    /**
     * 拒绝旧别名：服务端不识别 challengeToken 等字段名对应的 class。
     */
    public static MfaTokenClass parseCanonical(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.trim().toUpperCase();
        // 明确拒绝旧别名
        if ("CHALLENGE".equals(n) || "PREAUTH".equals(n) || "PRE_AUTH_TOKEN".equals(n)
                || "ENROLLMENT_TOKEN".equals(n) || "RECOVERY_TOKEN".equals(n)
                || "CHALLENGE_TOKEN".equals(n)) {
            return null;
        }
        try {
            return MfaTokenClass.valueOf(n);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
