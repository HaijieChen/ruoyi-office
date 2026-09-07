package cn.iocoder.yudao.module.bpm.service.oa;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only: record overtime/punch SQL after real proceed, to assert tenant_id injection.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "update", args = {
                MappedStatement.class, Object.class
        })
})
final class TenantSqlCaptureInterceptor implements Interceptor {

    private final List<String> sqls = new CopyOnWriteArrayList<>();

    void clear() {
        sqls.clear();
    }

    List<String> snapshot() {
        return new ArrayList<>(sqls);
    }

    boolean anyContainsTenantId() {
        return sqls.stream().anyMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("tenant_id"));
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;
        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql == null || boundSql.getSql() == null ? "" : boundSql.getSql();
        String compact = sql.replaceAll("\\s+", " ").trim();
        String lower = compact.toLowerCase(Locale.ROOT);
        if (lower.contains("bpm_oa_overtime") || lower.contains("bpm_oa_punch_correction")) {
            sqls.add(compact);
        }
        return result;
    }
}
