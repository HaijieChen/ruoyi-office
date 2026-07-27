package cn.iocoder.yudao.module.finance.enums;

public enum FinanceReceiptClaimStatusEnum {

    UNCLAIMED(0, "待认领"),
    PARTIALLY_CLAIMED(1, "部分认领"),
    FULLY_CLAIMED(2, "完全认领"),
    CLOSED(3, "已关闭");

    private final Integer status;
    private final String name;

    FinanceReceiptClaimStatusEnum(Integer status, String name) {
        this.status = status;
        this.name = name;
    }

    public Integer getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

}
