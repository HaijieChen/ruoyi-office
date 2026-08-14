package cn.iocoder.yudao.module.system.service.mfa.enums;

/**
 * R-01 骨架：Token 类别。仅 {@link #ACCESS} 可作为业务 Bearer。
 * <p>
 * challenge / enrollment / recovery 句柄不得当作业务 Authorization。
 */
public enum MfaTokenClass {

    /** 正常业务会话 access token */
    ACCESS,
    /** 预认证 MFA challenge 句柄 */
    PRE_AUTH,
    /** 绑定 enrollment 句柄 */
    ENROLLMENT,
    /** 备份码恢复会话（限权，无 refresh） */
    RECOVERY;

    public boolean isBusinessBearer() {
        return this == ACCESS;
    }

    /**
     * 默认拒绝：未知或非 ACCESS 均不可访问业务 API。
     */
    public static boolean allowsBusinessApi(MfaTokenClass tokenClass) {
        return tokenClass != null && tokenClass.isBusinessBearer();
    }

}
