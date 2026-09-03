package cn.iocoder.yudao.framework.datasource.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.util.List;

/**
 * 定时打印 Druid 池占用，不借新连接。
 */
@Slf4j
public class DruidPoolMonitor {

    private final DataSource dataSource;

    public DruidPoolMonitor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void logPool() {
        List<DruidPoolSnapshot> snapshots = DruidPoolSnapshot.collect(dataSource);
        if (snapshots.isEmpty()) {
            return;
        }
        for (DruidPoolSnapshot snapshot : snapshots) {
            if (snapshot.isUnhealthy() || snapshot.isNearlyFull()) {
                log.warn("[druid][pool] name={} active={}/{} pooling={} waitThreads={}",
                        snapshot.getName(), snapshot.getActiveCount(), snapshot.getMaxActive(),
                        snapshot.getPoolingCount(), snapshot.getWaitThreadCount());
            } else {
                log.info("[druid][pool] name={} active={}/{} pooling={} waitThreads={}",
                        snapshot.getName(), snapshot.getActiveCount(), snapshot.getMaxActive(),
                        snapshot.getPoolingCount(), snapshot.getWaitThreadCount());
            }
        }
    }

}
