package cn.iocoder.yudao.module.finance.dal.redis.no;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceExpenseReimbursementNoRedisDAOTest {

    @Test
    void shouldGenerateExpenseNoWithDateAndDailySequence() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("finance_expense_reimbursement_no:20260831")).thenReturn(4L);

        FinanceExpenseReimbursementNoRedisDAO redisDAO = new FinanceExpenseReimbursementNoRedisDAO(stringRedisTemplate);

        assertEquals("EXP-20260831-4", redisDAO.generate(LocalDate.of(2026, 8, 31)));
    }

}
