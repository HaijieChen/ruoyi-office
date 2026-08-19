package cn.iocoder.yudao.module.finance.service.expense;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FinanceExpensePredocServiceImpl implements FinanceExpensePredocService {

    private static final int APPROVE = 2;

    private final JdbcTemplate jdbcTemplate;

    public FinanceExpensePredocServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isApprovedTrip(Long userId, String processInstanceId) {
        if (userId == null || processInstanceId == null || processInstanceId.isBlank()) {
            return false;
        }
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM bpm_oa_business_trip WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0",
                Integer.class, processInstanceId.trim(), userId, APPROVE);
        return n != null && n > 0;
    }

    @Override
    public boolean isApprovedOuting(Long userId, String processInstanceId) {
        if (userId == null || processInstanceId == null || processInstanceId.isBlank()) {
            return false;
        }
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM bpm_oa_outing WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0",
                Integer.class, processInstanceId.trim(), userId, APPROVE);
        return n != null && n > 0;
    }
}
