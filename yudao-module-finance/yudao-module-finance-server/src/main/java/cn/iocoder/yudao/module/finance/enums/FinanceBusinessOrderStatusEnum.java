package cn.iocoder.yudao.module.finance.enums;

public enum FinanceBusinessOrderStatusEnum {

    DRAFT(0, "草稿"),
    ACTIVE(1, "有效"),
    CLOSED(2, "关闭");

    private final Integer status;
    private final String name;

    FinanceBusinessOrderStatusEnum(Integer status, String name) {
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
        for (FinanceBusinessOrderStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return true;
            }
        }
        return false;
    }

}
