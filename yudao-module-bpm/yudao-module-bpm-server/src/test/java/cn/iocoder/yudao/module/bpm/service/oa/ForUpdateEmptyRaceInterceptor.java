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
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only MyBatis interceptor: after a real FOR UPDATE query returns, record
 * rowCount and the TX-bound connection isolation, then wait on a barrier.
 * Does not change lock semantics. Green tests leave {@link #armed} false.
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        })
})
final class ForUpdateEmptyRaceInterceptor implements Interceptor {

    private final DataSource dataSource;
    private final AtomicBoolean armed = new AtomicBoolean(false);
    private volatile CyclicBarrier barrier;
    private volatile long barrierTimeoutMs = 3000L;
    private final Map<Long, Observation> observations = new ConcurrentHashMap<>();
    private final AtomicInteger barrierTimeouts = new AtomicInteger();

    ForUpdateEmptyRaceInterceptor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void arm(CyclicBarrier barrier, long barrierTimeoutMs) {
        this.observations.clear();
        this.barrierTimeouts.set(0);
        this.barrier = barrier;
        this.barrierTimeoutMs = barrierTimeoutMs;
        this.armed.set(true);
    }

    void disarm() {
        this.armed.set(false);
        this.barrier = null;
    }

    Map<Long, Observation> observations() {
        return observations;
    }

    int barrierTimeouts() {
        return barrierTimeouts.get();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        if (!armed.get()) {
            return result;
        }
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql == null || boundSql.getSql() == null ? "" : boundSql.getSql();
        if (!sql.toUpperCase().contains("FOR UPDATE")) {
            return result;
        }
        int rowCount = 0;
        if (result instanceof Collection<?> collection) {
            rowCount = collection.size();
        }
        String isolation = readBoundIsolation();
        long threadId = Thread.currentThread().getId();
        observations.put(threadId, new Observation(threadId, rowCount, isolation, sql.contains("FOR UPDATE")));
        CyclicBarrier current = barrier;
        if (current != null) {
            try {
                current.await(barrierTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | InterruptedException ex) {
                barrierTimeouts.incrementAndGet();
                Thread.interrupted();
            } catch (Exception ex) {
                barrierTimeouts.incrementAndGet();
            }
        }
        return result;
    }

    private String readBoundIsolation() {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT @@session.transaction_isolation")) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return "UNKNOWN";
        } catch (Exception ex) {
            return "ERROR:" + ex.getClass().getSimpleName();
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    record Observation(long threadId, int rowCount, String isolation, boolean forUpdate) {
        boolean emptyRead() {
            return forUpdate && rowCount == 0;
        }
    }
}
