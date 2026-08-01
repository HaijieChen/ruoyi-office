package cn.iocoder.yudao.module.finance.enums;

/**
 * 合同签约申请审批状态（与 finance_contract_application.approval_status 一致）
 */
public enum FinanceContractApprovalStatusEnum {

    PENDING("PENDING", "审批中"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已取消");

    private final String status;
    private final String name;

    FinanceContractApprovalStatusEnum(String status, String name) {
        this.status = status;
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }
}
