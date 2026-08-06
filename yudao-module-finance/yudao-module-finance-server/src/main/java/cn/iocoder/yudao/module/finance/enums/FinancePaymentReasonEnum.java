package cn.iocoder.yudao.module.finance.enums;

/**
 * 付款事由
 */
public enum FinancePaymentReasonEnum {

    BUSINESS("BUSINESS", "业务付款"),
    PURCHASE("PURCHASE", "采购付款"),
    SALARY("SALARY", "薪资"),
    TAX("TAX", "税费"),
    LEASE("LEASE", "房屋租赁"),
    OTHER("OTHER", "其他");

    private final String code;
    private final String name;

    FinancePaymentReasonEnum(String code, String name) {
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
        for (FinancePaymentReasonEnum item : values()) {
            if (item.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
