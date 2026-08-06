package cn.iocoder.yudao.module.finance.enums;

/**
 * 支付时效（CF-P1 单字段）
 */
public enum FinancePaymentTimingEnum {

    IMMEDIATE("IMMEDIATE", "即时"),
    MONTH_END("MONTH_END", "月底"),
    ON_NOTICE("ON_NOTICE", "通知");

    private final String code;
    private final String name;

    FinancePaymentTimingEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static boolean contains(String code) {
        if (code == null) {
            return false;
        }
        for (FinancePaymentTimingEnum item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
