package cn.iocoder.yudao.module.finance.enums;

/**
 * 付款申请状态（与 finance_payment_application.status 一致）
 */
public enum FinancePaymentApplicationStatusEnum {

    PENDING("PENDING", "审批中"),
    WAIT_PAY("WAIT_PAY", "待支付"),
    PARTIAL_PAID("PARTIAL_PAID", "部分支付"),
    PAID("PAID", "已支付"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已取消");

    private final String status;
    private final String name;

    FinancePaymentApplicationStatusEnum(String status, String name) {
        this.status = status;
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public static boolean contains(String status) {
        if (status == null) {
            return false;
        }
        for (FinancePaymentApplicationStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return true;
            }
        }
        return false;
    }
}
