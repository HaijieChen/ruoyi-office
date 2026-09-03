package cn.iocoder.yudao.framework.datasource.core;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 连接池打满或出现等待线程时 health DOWN。不借新连接。
 */
public class DruidPoolHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DruidPoolHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        List<DruidPoolSnapshot> snapshots = DruidPoolSnapshot.collect(dataSource);
        if (snapshots.isEmpty()) {
            return Health.unknown().withDetail("druid", "no pool").build();
        }
        boolean down = false;
        Health.Builder builder = Health.up();
        for (DruidPoolSnapshot snapshot : snapshots) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("active", snapshot.getActiveCount());
            detail.put("max", snapshot.getMaxActive());
            detail.put("pooling", snapshot.getPoolingCount());
            detail.put("waitThreads", snapshot.getWaitThreadCount());
            builder.withDetail(snapshot.getName(), detail);
            if (snapshot.isUnhealthy()) {
                down = true;
            }
        }
        return down ? builder.status("DOWN").build() : builder.build();
    }

}
