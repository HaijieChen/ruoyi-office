package cn.iocoder.yudao.module.finance.enums;

/**
 * 到款认领明细来源（finance_receipt_claim_item.claim_source）
 */
public enum FinanceReceiptClaimSourceEnum {

    INVOICE("INVOICE", "挂开票申请"),
    LEGACY_BO("LEGACY_BO", "历史挂商务单");

    private final String source;
    private final String name;

    FinanceReceiptClaimSourceEnum(String source, String name) {
        this.source = source;
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public String getName() {
        return name;
    }

    public static boolean contains(String source) {
        if (source == null) {
            return false;
        }
        for (FinanceReceiptClaimSourceEnum item : values()) {
            if (item.source.equals(source)) {
                return true;
            }
        }
        return false;
    }

}
