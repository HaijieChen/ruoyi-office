package cn.iocoder.yudao.module.system.service.mfa.enums;

import java.util.Locale;

/**
 * MFA 模式枚举（类型化，禁止裸字符串默认 OFF）。
 */
public enum MfaMode {

    OFF,
    OPTIONAL,
    REQUIRED,
    /** 仅租户策略可用：继承全局 */
    INHERIT;

    /**
     * 严格解析；非法值返回 null（由调用方进入 DEGRADED_CLOSED，不得当 OFF）。
     */
    public static MfaMode parseStrict(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MfaMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isNonOff() {
        return this == OPTIONAL || this == REQUIRED;
    }

}
