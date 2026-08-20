package cn.iocoder.yudao.module.finance.service.expense;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementLineReqVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceExpenseStayStandardTest {

    private static FinanceExpenseReimbursementLineReqVO travel(String amount, String reason) {
        FinanceExpenseReimbursementLineReqVO line = new FinanceExpenseReimbursementLineReqVO();
        line.setLineKind("NORMAL");
        line.setCategory("travel");
        line.setFeeDate(LocalDate.of(2026, 8, 1));
        line.setAmount(new BigDecimal(amount));
        line.setOverLimitReason(reason);
        return line;
    }

    @Test
    void hangzhouOver300RequiresReason() {
        assertThrows(ServiceException.class,
                () -> FinanceExpenseReimbursementServiceImpl.applyStayStandard(travel("300.01", null), "杭州"));
        assertDoesNotThrow(() -> FinanceExpenseReimbursementServiceImpl.applyStayStandard(
                travel("300.01", "客户指定酒店"), "杭州"));
        FinanceExpenseReimbursementLineReqVO ok = travel("300.00", null);
        FinanceExpenseReimbursementServiceImpl.applyStayStandard(ok, "杭州");
        assertEquals("OTHER", ok.getStayCityTier());
    }

    @Test
    void beijingAllows400WithoutReason() {
        FinanceExpenseReimbursementLineReqVO ok = travel("400.00", null);
        assertDoesNotThrow(() -> FinanceExpenseReimbursementServiceImpl.applyStayStandard(ok, "北京"));
        assertEquals("T1", ok.getStayCityTier());
        assertThrows(ServiceException.class,
                () -> FinanceExpenseReimbursementServiceImpl.applyStayStandard(travel("400.01", ""), "上海"));
    }

    @Test
    void missingCityRejected() {
        assertThrows(ServiceException.class,
                () -> FinanceExpenseReimbursementServiceImpl.applyStayStandard(travel("10", null), null));
    }

    @Test
    void nonTravelIgnoresCity() {
        FinanceExpenseReimbursementLineReqVO line = travel("999", null);
        line.setCategory("office");
        assertDoesNotThrow(() -> FinanceExpenseReimbursementServiceImpl.applyStayStandard(line, null));
    }
}
