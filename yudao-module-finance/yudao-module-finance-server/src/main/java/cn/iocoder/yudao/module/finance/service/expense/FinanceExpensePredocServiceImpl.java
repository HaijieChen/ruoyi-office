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

    @Override
    public String resolveCity(Long userId, String predocType, String processInstanceId) {
        if (userId == null || processInstanceId == null || processInstanceId.isBlank() || predocType == null) {
            return null;
        }
        String sql;
        if (TYPE_TRIP.equals(predocType)) {
            sql = "SELECT destination FROM bpm_oa_business_trip WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0 LIMIT 1";
        } else if (TYPE_OUTING.equals(predocType)) {
            sql = "SELECT location FROM bpm_oa_outing WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0 LIMIT 1";
        } else {
            return null;
        }
        return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null,
                processInstanceId.trim(), userId, APPROVE);
    }

    @Override
    public StayStay resolveStay(Long userId, String predocType, String processInstanceId) {
        if (userId == null || processInstanceId == null || processInstanceId.isBlank() || predocType == null) {
            return null;
        }
        if (TYPE_TRIP.equals(predocType)) {
            return jdbcTemplate.query(
                    "SELECT destination, start_time, end_time, user_id, companion_user_ids, companion_user_id "
                            + "FROM bpm_oa_business_trip WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0 LIMIT 1",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        java.util.List<Long> companions = parseIds(rs.getString("companion_user_ids"));
                        if (companions.isEmpty()) {
                            long legacy = rs.getLong("companion_user_id");
                            if (!rs.wasNull()) {
                                companions.add(legacy);
                            }
                        }
                        java.sql.Timestamp start = rs.getTimestamp("start_time");
                        java.sql.Timestamp end = rs.getTimestamp("end_time");
                        return new StayStay(rs.getString("destination"),
                                start == null ? null : start.toLocalDateTime().toLocalDate(),
                                end == null ? null : end.toLocalDateTime().toLocalDate(),
                                rs.getLong("user_id"), companions);
                    }, processInstanceId.trim(), userId, APPROVE);
        }
        if (TYPE_OUTING.equals(predocType)) {
            return jdbcTemplate.query(
                    "SELECT location, start_time, end_time, user_id FROM bpm_oa_outing "
                            + "WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0 LIMIT 1",
                    rs -> {
                        if (!rs.next()) {
                            return null;
                        }
                        java.sql.Timestamp start = rs.getTimestamp("start_time");
                        java.sql.Timestamp end = rs.getTimestamp("end_time");
                        return new StayStay(rs.getString("location"),
                                start == null ? null : start.toLocalDateTime().toLocalDate(),
                                end == null ? null : end.toLocalDateTime().toLocalDate(),
                                rs.getLong("user_id"), java.util.List.of());
                    }, processInstanceId.trim(), userId, APPROVE);
        }
        return null;
    }

    static java.util.List<Long> parseIds(String raw) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String p : raw.split(",")) {
            String t = p.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(t));
            } catch (NumberFormatException ignored) {
                // skip JSON leftovers
            }
        }
        return ids;
    }
}
