package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * ADR-MFA-v3 §7：flow bearer 的 tokenClass。禁止旧别名 challenge/preAuth/enrollment/recovery token。
 * <p>
 * 业务 API 仅接受 {@link MfaTokenClass#ACCESS}。
 */
public enum MfaFlowTokenClass {
    PRE_AUTH,
    ENROLLMENT,
    RECOVERY;

    public boolean isBusinessBearer() {
        return false;
    }
}
