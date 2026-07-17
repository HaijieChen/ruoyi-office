package cn.iocoder.yudao.module.bpm.framework.datasource;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Holder for the dynamic-form read-only connection pool.
 *
 * <p>The holder deliberately does not implement {@link javax.sql.DataSource}. Publishing this pool as a
 * {@code DataSource} bean would make dynamic-datasource's {@code @ConditionalOnMissingBean} back off and could
 * replace the platform's primary routing data source.</p>
 */
public final class BpmFormReadOnlyPool implements AutoCloseable {

    private final HikariDataSource dataSource;

    public BpmFormReadOnlyPool(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    HikariDataSource getDataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }

}
