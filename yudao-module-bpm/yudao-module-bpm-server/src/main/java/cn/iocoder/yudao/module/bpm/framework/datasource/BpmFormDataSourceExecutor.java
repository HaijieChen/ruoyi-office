package cn.iocoder.yudao.module.bpm.framework.datasource;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;

/** Executes a validated SELECT through the dedicated read-only connection pool. */
public class BpmFormDataSourceExecutor {

    private final DataSource dataSource;

    public BpmFormDataSourceExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Map<String, Object>> execute(String sql, Map<String, Object> parameters,
                                              int maxRows, int timeoutSeconds) {
        ParsedSql parsed = parseNamedParameters(sql);
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(parsed.sql())) {
                statement.setMaxRows(maxRows);
                statement.setQueryTimeout(timeoutSeconds);
                for (int i = 0; i < parsed.parameterNames().size(); i++) {
                    String name = parsed.parameterNames().get(i);
                    if (!parameters.containsKey(name)) {
                        throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_PARAM_MISSING, name);
                    }
                    statement.setObject(i + 1, parameters.get(name));
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    return readRows(resultSet);
                }
            }
        } catch (SQLTimeoutException ex) {
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_TIMEOUT);
        } catch (SQLException ex) {
            // JDBC messages may contain SQL or values, so they are deliberately not propagated.
            throw ServiceExceptionUtil.exception(BPM_DATA_SOURCE_EXECUTION_FAILED);
        }
    }

    private static List<Map<String, Object>> readRows(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metadata.getColumnLabel(i), normalizeJdbcValue(resultSet.getObject(i)));
            }
            rows.add(row);
        }
        return rows;
    }

    /** Replaces named parameters outside SQL string literals and preserves repeated parameter order. */
    static ParsedSql parseNamedParameters(String sql) {
        StringBuilder normalized = new StringBuilder(sql.length());
        List<String> names = new ArrayList<>();
        boolean inString = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (current == '\'') {
                normalized.append(current);
                if (inString && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    normalized.append(sql.charAt(++i));
                } else {
                    inString = !inString;
                }
                continue;
            }
            if (!inString && current == ':' && i + 1 < sql.length()
                    && Character.isJavaIdentifierStart(sql.charAt(i + 1))) {
                int end = i + 2;
                while (end < sql.length() && Character.isJavaIdentifierPart(sql.charAt(end))) {
                    end++;
                }
                names.add(sql.substring(i + 1, end));
                normalized.append('?');
                i = end - 1;
            } else {
                normalized.append(current);
            }
        }
        return new ParsedSql(normalized.toString(), names);
    }

    public static boolean referencesNamedParameter(String sql, String parameterName) {
        return sql != null && parameterName != null
                && parseNamedParameters(sql).parameterNames().contains(parameterName);
    }

    private static Object normalizeJdbcValue(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value;
    }

    record ParsedSql(String sql, List<String> parameterNames) {
    }

}
