package cn.iocoder.yudao.module.finance.service.contract;

import cn.iocoder.yudao.module.finance.dal.dataobject.contract.FinanceContractApplicationDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceApprovedSalesBusinessOrderSupportTest {

    private static FinanceContractApplicationDO sales(boolean amountNa, String amount) {
        return FinanceContractApplicationDO.builder()
                .id(1L)
                .fileType("销售合同")
                .amountNa(amountNa)
                .contractAmount(amount == null ? null : new BigDecimal(amount))
                .build();
    }

    @Test
    void onlySalesWithAmountAndNoExistingBo() {
        assertTrue(FinanceApprovedSalesBusinessOrderSupport.shouldCreate(sales(false, "100"), 0));
        assertFalse(FinanceApprovedSalesBusinessOrderSupport.shouldCreate(sales(false, "100"), 1));
        assertFalse(FinanceApprovedSalesBusinessOrderSupport.shouldCreate(sales(true, "100"), 0));
        assertFalse(FinanceApprovedSalesBusinessOrderSupport.shouldCreate(sales(false, "0"), 0));
        FinanceContractApplicationDO purchase = sales(false, "100");
        purchase.setFileType("采购合同");
        assertFalse(FinanceApprovedSalesBusinessOrderSupport.shouldCreate(purchase, 0));
    }
}
