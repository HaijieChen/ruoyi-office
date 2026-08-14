package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * 用户 enrollment 状态（assurance 权威）。
 */
public enum MfaEnrollmentState {
    NONE,
    PENDING,
    COMPLETED;

    public static MfaEnrollmentState parseStrict(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MfaEnrollmentState.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
