package cn.iocoder.yudao.module.system.service.mfa.support;

import cn.iocoder.yudao.module.system.service.mfa.enums.MfaTokenClass;

/**
 * R-01 骨架：业务 API 默认拒绝非 ACCESS 的 tokenClass。
 * <p>
 * 完整过滤器在安全过滤器链挂载（后续切片）；本类提供可单测的判定逻辑。
 */
public final class MfaTokenClassGuard {

    public static final String ATTR_TOKEN_CLASS = "tokenClass";

    private MfaTokenClassGuard() {
    }

    /**
     * @param tokenClassRaw 来自 token 声明 / userInfo 的 tokenClass；null 视为历史 ACCESS（兼容）
     * @return true 允许访问业务 API
     */
    public static boolean allowsBusinessApi(String tokenClassRaw) {
        if (tokenClassRaw == null || tokenClassRaw.isBlank()) {
            // 历史 access token 无声明：兼容放行；challenge 句柄不会进入 OAuth access 校验
            return true;
        }
        try {
            return MfaTokenClass.allowsBusinessApi(MfaTokenClass.valueOf(tokenClassRaw.trim()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * challenge / enrollment / recovery 句柄不得当作 Bearer。
     */
    public static boolean isChallengeHandleClass(String tokenClassRaw) {
        if (tokenClassRaw == null) {
            return false;
        }
        try {
            MfaTokenClass c = MfaTokenClass.valueOf(tokenClassRaw.trim());
            return c == MfaTokenClass.PRE_AUTH
                    || c == MfaTokenClass.ENROLLMENT
                    || c == MfaTokenClass.RECOVERY;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

}
