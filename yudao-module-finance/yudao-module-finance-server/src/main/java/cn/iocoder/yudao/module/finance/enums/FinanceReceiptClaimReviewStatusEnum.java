package cn.iocoder.yudao.module.finance.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FinanceReceiptClaimReviewStatusEnum {

    PENDING(0, "待确认"),
    CONFIRMED(1, "已确认"),
    REJECTED(2, "已驳回"),
    REVOKED(3, "已撤销");

    private final Integer status;
    private final String name;

}
