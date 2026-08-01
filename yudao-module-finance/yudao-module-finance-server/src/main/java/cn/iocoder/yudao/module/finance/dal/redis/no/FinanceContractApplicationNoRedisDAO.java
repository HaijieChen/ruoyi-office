package cn.iocoder.yudao.module.finance.dal.redis.no;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static cn.iocoder.yudao.module.finance.dal.redis.RedisKeyConstants.FINANCE_CONTRACT_APPLICATION_NO;

@Repository
public class FinanceContractApplicationNoRedisDAO {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;

    public FinanceContractApplicationNoRedisDAO(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String generate(LocalDate date) {
        String dateText = DATE_FORMATTER.format(date);
        Long sequence = stringRedisTemplate.opsForValue()
                .increment(String.format(FINANCE_CONTRACT_APPLICATION_NO, dateText));
        return "CT-" + dateText + "-" + sequence;
    }
}
