package cn.iocoder.yudao.module.finance.enums;

public enum FinanceReceiptLifecycleActionEnum {

    CLOSE(1, "关闭"),
    REOPEN(2, "重开");

    private final Integer action;
    private final String name;

    FinanceReceiptLifecycleActionEnum(Integer action, String name) {
        this.action = action;
        this.name = name;
    }

    public Integer getAction() {
        return action;
    }

    public String getName() {
        return name;
    }
}
