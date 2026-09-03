package cn.iocoder.yudao.framework.datasource.core;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Druid 连接池快照。只读计数，不借连接。
 */
public final class DruidPoolSnapshot {

    private final String name;
    private final int activeCount;
    private final int poolingCount;
    private final int maxActive;
    private final int waitThreadCount;

    public DruidPoolSnapshot(String name, int activeCount, int poolingCount, int maxActive, int waitThreadCount) {
        this.name = name;
        this.activeCount = activeCount;
        this.poolingCount = poolingCount;
        this.maxActive = maxActive;
        this.waitThreadCount = waitThreadCount;
    }

    public String getName() {
        return name;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public int getPoolingCount() {
        return poolingCount;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public int getWaitThreadCount() {
        return waitThreadCount;
    }

    public boolean isUnhealthy() {
        return waitThreadCount > 0 || (maxActive > 0 && activeCount >= maxActive);
    }

    public boolean isNearlyFull() {
        return maxActive > 0 && activeCount * 10 >= maxActive * 8;
    }

    public static List<DruidPoolSnapshot> collect(DataSource dataSource) {
        if (dataSource == null) {
            return Collections.emptyList();
        }
        if (dataSource instanceof DynamicRoutingDataSource dynamic) {
            Map<String, DataSource> sources = dynamic.getDataSources();
            List<DruidPoolSnapshot> snapshots = new ArrayList<>(sources.size());
            for (Map.Entry<String, DataSource> entry : sources.entrySet()) {
                DruidPoolSnapshot snapshot = fromOne(entry.getKey(), entry.getValue());
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }
            return snapshots;
        }
        DruidPoolSnapshot snapshot = fromOne("default", dataSource);
        return snapshot == null ? Collections.emptyList() : List.of(snapshot);
    }

    static DruidPoolSnapshot fromOne(String name, DataSource dataSource) {
        DataSource unwrapped = unwrap(dataSource);
        if (!(unwrapped instanceof DruidDataSource druid)) {
            return null;
        }
        return new DruidPoolSnapshot(name, druid.getActiveCount(), druid.getPoolingCount(),
                druid.getMaxActive(), druid.getWaitThreadCount());
    }

    private static DataSource unwrap(DataSource dataSource) {
        DataSource current = dataSource;
        for (int i = 0; i < 4 && current != null; i++) {
            if (current instanceof DruidDataSource) {
                return current;
            }
            try {
                Method method = current.getClass().getMethod("getDataSource");
                Object nested = method.invoke(current);
                if (nested instanceof DataSource nestedSource) {
                    current = nestedSource;
                    continue;
                }
            } catch (ReflectiveOperationException ignored) {
                break;
            }
            break;
        }
        return current;
    }

}
