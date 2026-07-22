package cn.iocoder.yudao.module.finance.dal.redis.no;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinanceReceiptNoRedisDAOTest {

    @Test
    void shouldGenerateReceiptNoWithDateAndDailySequence() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("finance_receipt_no:20260722")).thenReturn(7L);

        FinanceReceiptNoRedisDAO redisDAO = new FinanceReceiptNoRedisDAO(stringRedisTemplate);

        assertEquals("RC-20260722-7", redisDAO.generate(LocalDate.of(2026, 7, 22)));
    }

}
