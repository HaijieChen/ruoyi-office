package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * ADR-MFA-v3 §5 / §7：auth flow 状态（切片 2）。
 */
public enum MfaAuthFlowState {
    ACTIVE,
    COMPLETED,
    EXPIRED,
    REVOKED
}
