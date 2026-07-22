package cn.iocoder.yudao.module.finance.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinanceReceiptClaimStatusEnumTest {

    @Test
    void shouldExposeReceiptClaimStatusValues() {
        assertEquals(0, FinanceReceiptClaimStatusEnum.UNCLAIMED.getStatus());
        assertEquals(1, FinanceReceiptClaimStatusEnum.PARTIALLY_CLAIMED.getStatus());
        assertEquals(2, FinanceReceiptClaimStatusEnum.FULLY_CLAIMED.getStatus());
        assertEquals(3, FinanceReceiptClaimStatusEnum.CLOSED.getStatus());
    }

}
