package cn.iocoder.yudao.module.finance.enums;

/**
 * 付款申请业务类型（独立入口 + 共享支付底座）。
 */
public enum FinancePaymentApplicationKindEnum {

    ORDINARY("ORDINARY", "普通付款"),
    SALARY("SALARY", "薪资付款"),
    TAX("TAX", "税金付款");

    private final String code;
    private final String name;

    FinancePaymentApplicationKindEnum(String code, String name) {
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
        for (FinancePaymentApplicationKindEnum item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
