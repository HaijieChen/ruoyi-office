package cn.iocoder.yudao.module.finance.service.common;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.expense.FinanceExpenseReimbursementLineDO;
import cn.iocoder.yudao.module.finance.dal.dataobject.payment.FinancePaymentApplicationDO;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementLineMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.expense.FinanceExpenseReimbursementMapper;
import cn.iocoder.yudao.module.finance.dal.mysql.payment.FinancePaymentApplicationMapper;
import cn.iocoder.yudao.module.finance.service.expense.FinanceExpensePredocService;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceBpmAttachAccessImplTest {

    private FinanceExpenseReimbursementLineMapper lineMapper;
    private FinanceExpenseReimbursementMapper expenseMapper;
    private FinanceExpensePredocService predoc;
    private FinancePaymentApplicationMapper paymentMapper;
    private FinancePaymentApplicationService paymentService;
    private SecurityFrameworkService security;
    private FinanceBpmAttachAccessImpl access;

    @BeforeEach
    void setUp() {
        lineMapper = mock(FinanceExpenseReimbursementLineMapper.class);
        expenseMapper = mock(FinanceExpenseReimbursementMapper.class);
        predoc = mock(FinanceExpensePredocService.class);
        paymentMapper = mock(FinancePaymentApplicationMapper.class);
        paymentService = mock(FinancePaymentApplicationService.class);
        security = mock(SecurityFrameworkService.class);
        access = new FinanceBpmAttachAccessImpl(lineMapper, expenseMapper, predoc, paymentMapper, paymentService, security);
    }

    @Test
    void expenseAssigneeCanReadAttachedTripPi() {
        FinanceExpenseReimbursementLineDO line = new FinanceExpenseReimbursementLineDO();
        line.setReimbursementId(88L);
        line.setPredocProcessInstanceId("trip-pi");
        when(lineMapper.selectList(any())).thenReturn(List.of(line));
        when(expenseMapper.selectById(88L)).thenReturn(FinanceExpenseReimbursementDO.builder()
                .id(88L).applicantUserId(1L).processInstanceId("exp-pi").build());
        when(predoc.isProcessAssignee("exp-pi", 9L)).thenReturn(true);
        when(paymentMapper.selectList(any())).thenReturn(List.of());

        assertTrue(access.canReadProcessInstanceViaBill(9L, "trip-pi"));
    }

    @Test
    void strangerCannotReadUnattachedPi() {
        when(lineMapper.selectList(any())).thenReturn(List.of());
        when(paymentMapper.selectList(any())).thenReturn(List.of());
        assertFalse(access.canReadProcessInstanceViaBill(9L, "trip-pi"));
    }

    @Test
    void paymentHistoricAssigneeCanReadAttachedContract() {
        FinancePaymentApplicationDO payment = new FinancePaymentApplicationDO();
        payment.setId(7L);
        payment.setLeaseContractApplicationId(55L);
        payment.setProcessInstanceId("pay-pi");
        when(paymentMapper.selectList(any())).thenReturn(List.of(payment), List.of());
        when(paymentService.canAccessDetail(7L, 9L)).thenReturn(false);
        when(predoc.isProcessAssignee("pay-pi", 9L)).thenReturn(true);

        assertTrue(access.canReadContractViaBill(9L, 55L));
        assertFalse(access.canReadContractViaBill(9L, 99L));
    }
}
