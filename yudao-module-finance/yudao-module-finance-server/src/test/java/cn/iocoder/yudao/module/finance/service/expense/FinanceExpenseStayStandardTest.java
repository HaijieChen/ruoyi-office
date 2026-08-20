package cn.iocoder.yudao.module.finance.service.expense;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.finance.controller.admin.expense.vo.FinanceExpenseReimbursementLineReqVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinanceExpenseStayStandardTest {

    private static FinanceExpenseReimbursementLineReqVO travel(String tier, String amount, String reason) {
        FinanceExpenseReimbursementLineReqVO line = new FinanceExpenseReimbursementLineReqVO();
        line.setLineKind("NORMAL");
        line.setCategory("travel");
        line.setFeeDate(LocalDate.of(2026, 8, 1));
        line.setAmount(new BigDecimal(amount));
        line.setStayCityTier(tier);
        line.setOverLimitReason(reason);
        return line;
    }

    @Test
    void otherCityOver300RequiresReason() {
        assertThrows(ServiceException.class,
                () -> FinanceExpenseReimbursementServiceImpl.validateStayStandard(travel("OTHER", "300.01", null)));
        assertDoesNotThrow(() -> FinanceExpenseReimbursementServiceImpl.validateStayStandard(
                travel("OTHER", "300.01", "客户指定酒店")));
        assertDoesNotThrow(() -> FinanceExpenseReimbursementServiceImpl.validateStayStandard(
                travel("OTHER", "300.00", null)));
    }

    @Test
    void t1CityAllows400WithoutReason() {
        assertDoesNotThrow(() -> FinanceExpenseReimbursementServiceImpl.validateStayStandard(
                travel("T1", "400.00", null)));
        assertThrows(ServiceException.class,
                () -> FinanceExpenseReimbursementServiceImpl.validateStayStandard(travel("T1", "400.01", "")));
    }

    @Test
    void nonTravelIgnoresTier() {
        FinanceExpenseReimbursementLineReqVO line = travel(null, "999", null);
        line.setCategory("office");
        assertDoesNotThrow(() -> FinanceExpenseReimbursementServiceImpl.validateStayStandard(line));
    }
}
