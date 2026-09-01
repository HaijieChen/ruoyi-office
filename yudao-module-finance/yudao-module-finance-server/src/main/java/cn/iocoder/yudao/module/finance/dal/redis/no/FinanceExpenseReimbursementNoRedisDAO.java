package cn.iocoder.yudao.module.finance.dal.redis.no;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static cn.iocoder.yudao.module.finance.dal.redis.RedisKeyConstants.FINANCE_EXPENSE_REIMBURSEMENT_NO;

@Repository
public class FinanceExpenseReimbursementNoRedisDAO {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate stringRedisTemplate;

    public FinanceExpenseReimbursementNoRedisDAO(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String generate(LocalDate date) {
        String dateText = DATE_FORMATTER.format(date);
        Long sequence = stringRedisTemplate.opsForValue()
                .increment(String.format(FINANCE_EXPENSE_REIMBURSEMENT_NO, dateText));
        return "EXP-" + dateText + "-" + sequence;
    }

}
