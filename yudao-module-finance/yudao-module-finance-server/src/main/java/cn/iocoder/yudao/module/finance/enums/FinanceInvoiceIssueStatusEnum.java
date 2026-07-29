package cn.iocoder.yudao.module.finance.enums;

/**
 * 开票申请/明细办票状态。
 * <p>表头：0 无行已开 / 1 部分行 / 2 全部行（D-T6）。
 * 明细行：0 未开 / 1 已开。
 */
public enum FinanceInvoiceIssueStatusEnum {

    NONE(0, "未开票"),
    PARTIAL(1, "部分开票"),
    FULL(2, "全部开票");

    private final Integer status;
    private final String name;

    FinanceInvoiceIssueStatusEnum(Integer status, String name) {
        this.status = status;
        this.name = name;
    }

    public Integer getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public static boolean contains(Integer status) {
        if (status == null) {
            return false;
        }
        for (FinanceInvoiceIssueStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return true;
            }
        }
        return false;
    }

}
