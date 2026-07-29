package cn.iocoder.yudao.module.finance.enums;

/**
 * 开票申请审批状态（与 finance_invoice_application.approval_status 一致）
 */
public enum FinanceInvoiceApprovalStatusEnum {

    PENDING("PENDING", "审批中"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已取消");

    private final String status;
    private final String name;

    FinanceInvoiceApprovalStatusEnum(String status, String name) {
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
        for (FinanceInvoiceApprovalStatusEnum item : values()) {
            if (item.status.equals(status)) {
                return true;
            }
        }
        return false;
    }

}
