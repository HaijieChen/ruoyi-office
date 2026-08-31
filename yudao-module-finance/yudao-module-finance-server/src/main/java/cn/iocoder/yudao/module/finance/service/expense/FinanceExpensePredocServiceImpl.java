package cn.iocoder.yudao.module.finance.service.expense;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.finance.service.common.FinanceRelatedProcessAccess;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FinanceExpensePredocServiceImpl implements FinanceExpensePredocService {

    private static final int APPROVE = 2;

    private final JdbcTemplate jdbcTemplate;
    private final FinanceRelatedProcessAccess relatedProcessAccess;
    private final ObjectProvider<TaskService> taskServiceProvider;
    private final ObjectProvider<HistoryService> historyServiceProvider;

    public FinanceExpensePredocServiceImpl(JdbcTemplate jdbcTemplate,
                                           FinanceRelatedProcessAccess relatedProcessAccess,
                                           ObjectProvider<TaskService> taskServiceProvider,
                                           ObjectProvider<HistoryService> historyServiceProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.relatedProcessAccess = relatedProcessAccess;
        this.taskServiceProvider = taskServiceProvider;
        this.historyServiceProvider = historyServiceProvider;
    }

    @Override
    public boolean isApprovedTrip(Long userId, String processInstanceId) {
        return isApprovedPredoc("bpm_oa_business_trip", userId, processInstanceId);
    }

    @Override
    public boolean isApprovedOuting(Long userId, String processInstanceId) {
        return isApprovedPredoc("bpm_oa_outing", userId, processInstanceId);
    }

    private boolean isApprovedPredoc(String table, Long userId, String processInstanceId) {
        if (userId == null || processInstanceId == null || processInstanceId.isBlank()) {
            return false;
        }
        String pid = processInstanceId.trim();
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + table + " WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0",
                Integer.class, pid, userId, APPROVE);
        if (n != null && n > 0) {
            return true;
        }
        if (!relatedProcessAccess.canAccessRelated(userId, pid)) {
            return false;
        }
        Integer shared = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + table + " WHERE process_instance_id = ? AND status = ? AND deleted = 0",
                Integer.class, pid, APPROVE);
        return shared != null && shared > 0;
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
        String pid = processInstanceId.trim();
        String city = jdbcTemplate.query(sql, rs -> rs.next() ? rs.getString(1) : null,
                pid, userId, APPROVE);
        if (city != null) {
            return city;
        }
        if (!relatedProcessAccess.canAccessRelated(userId, pid)) {
            return null;
        }
        String sharedSql = sql.replace(" AND user_id = ?", "");
        return jdbcTemplate.query(sharedSql, rs -> rs.next() ? rs.getString(1) : null, pid, APPROVE);
    }

    @Override
    public StayStay resolveStay(Long userId, String predocType, String processInstanceId) {
        if (userId == null || processInstanceId == null || processInstanceId.isBlank() || predocType == null) {
            return null;
        }
        String pid = processInstanceId.trim();
        if (TYPE_TRIP.equals(predocType)) {
            StayStay own = queryTripStay(pid, userId);
            if (own != null) {
                return own;
            }
            return relatedProcessAccess.canAccessRelated(userId, pid) ? queryTripStayShared(pid) : null;
        }
        if (TYPE_OUTING.equals(predocType)) {
            StayStay own = queryOutingStay(pid, userId);
            if (own != null) {
                return own;
            }
            return relatedProcessAccess.canAccessRelated(userId, pid) ? queryOutingStayShared(pid) : null;
        }
        return null;
    }

    private StayStay queryTripStay(String pid, Long userId) {
        return jdbcTemplate.query(
                "SELECT destination, start_time, end_time, user_id, companion_user_ids, companion_user_id "
                        + "FROM bpm_oa_business_trip WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0 LIMIT 1",
                this::mapTripStay, pid, userId, APPROVE);
    }

    private StayStay queryTripStayShared(String pid) {
        return jdbcTemplate.query(
                "SELECT destination, start_time, end_time, user_id, companion_user_ids, companion_user_id "
                        + "FROM bpm_oa_business_trip WHERE process_instance_id = ? AND status = ? AND deleted = 0 LIMIT 1",
                this::mapTripStay, pid, APPROVE);
    }

    private StayStay queryOutingStay(String pid, Long userId) {
        return jdbcTemplate.query(
                "SELECT location, start_time, end_time, user_id FROM bpm_oa_outing "
                        + "WHERE process_instance_id = ? AND user_id = ? AND status = ? AND deleted = 0 LIMIT 1",
                this::mapOutingStay, pid, userId, APPROVE);
    }

    private StayStay queryOutingStayShared(String pid) {
        return jdbcTemplate.query(
                "SELECT location, start_time, end_time, user_id FROM bpm_oa_outing "
                        + "WHERE process_instance_id = ? AND status = ? AND deleted = 0 LIMIT 1",
                this::mapOutingStay, pid, APPROVE);
    }

    private StayStay mapTripStay(java.sql.ResultSet rs) throws java.sql.SQLException {
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
    }

    private StayStay mapOutingStay(java.sql.ResultSet rs) throws java.sql.SQLException {
        if (!rs.next()) {
            return null;
        }
        java.sql.Timestamp start = rs.getTimestamp("start_time");
        java.sql.Timestamp end = rs.getTimestamp("end_time");
        return new StayStay(rs.getString("location"),
                start == null ? null : start.toLocalDateTime().toLocalDate(),
                end == null ? null : end.toLocalDateTime().toLocalDate(),
                rs.getLong("user_id"), java.util.List.of());
    }

    @Override
    public Long resolveBillPk(String predocType, String processInstanceId) {
        if (StrUtil.isBlank(predocType) || StrUtil.isBlank(processInstanceId)) {
            return null;
        }
        String table = TYPE_TRIP.equals(predocType)
                ? "bpm_oa_business_trip"
                : TYPE_OUTING.equals(predocType) ? "bpm_oa_outing" : null;
        if (table == null) {
            return null;
        }
        return jdbcTemplate.query(
                "SELECT id FROM " + table + " WHERE process_instance_id = ? AND deleted = 0 LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null,
                processInstanceId.trim());
    }

    @Override
    public boolean isProcessAssignee(String processInstanceId, Long userId) {
        if (userId == null || StrUtil.isBlank(processInstanceId)) {
            return false;
        }
        String uid = String.valueOf(userId);
        String pid = processInstanceId.trim();
        TaskService taskService = taskServiceProvider.getIfAvailable();
        if (taskService != null
                && taskService.createTaskQuery()
                .processInstanceId(pid)
                .taskCandidateOrAssigned(uid)
                .count() > 0) {
            return true;
        }
        HistoryService historyService = historyServiceProvider.getIfAvailable();
        if (historyService == null) {
            return false;
        }
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(pid)
                .taskAssignee(uid)
                .count() > 0;
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
