package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * 交互式登录响应判别式状态。
 */
public enum MfaLoginStatus {

    AUTHENTICATED,
    MFA_REQUIRED,
    MFA_ENROLLMENT_REQUIRED,
    MFA_RECOVERY_REQUIRED,
    MFA_POLICY_UNAVAILABLE

}
