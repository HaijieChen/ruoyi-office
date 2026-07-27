package cn.iocoder.yudao.module.finance.dal.redis.no;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static cn.iocoder.yudao.module.finance.dal.redis.RedisKeyConstants.FINANCE_RECEIPT_NO;

@Repository
public class FinanceReceiptNoRedisDAO {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;

    public FinanceReceiptNoRedisDAO(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String generate(LocalDate date) {
        String dateText = DATE_FORMATTER.format(date);
        Long sequence = stringRedisTemplate.opsForValue().increment(String.format(FINANCE_RECEIPT_NO, dateText));
        return "RC-" + dateText + "-" + sequence;
    }

}
