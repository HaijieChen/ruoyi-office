package cn.iocoder.yudao.module.bpm.framework.datasource;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class BpmFormDataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BpmFormDataSourceConfiguration.class);

    @Test
    void shouldStartWithoutJdbcUrlAndNotCreateReadOnlyPool() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BpmFormReadOnlyPool.class);
            assertThat(context).doesNotHaveBean(BpmFormDataSourceExecutor.class);
            assertThat(context).hasSingleBean(java.net.http.HttpClient.class);
            assertThat(context).doesNotHaveBean(DataSource.class);
        });
    }

    @Test
    void shouldCreateDedicatedPoolWithoutPublishingAnotherDataSourceBean() {
        contextRunner.withPropertyValues(
                        "bpm.form-data-source.jdbc.url=jdbc:h2:mem:bpm-form-config;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "bpm.form-data-source.jdbc.username=sa",
                        "bpm.form-data-source.jdbc.password=",
                        "bpm.form-data-source.jdbc.maximum-pool-size=99",
                        "bpm.form-data-source.jdbc.connection-timeout-ms=9999")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(BpmFormReadOnlyPool.class);
                    assertThat(context).hasSingleBean(BpmFormDataSourceExecutor.class);
                    assertThat(context).doesNotHaveBean(DataSource.class);

                    HikariDataSource dataSource = context.getBean(BpmFormReadOnlyPool.class).getDataSource();
                    assertThat(dataSource.getMaximumPoolSize()).isEqualTo(2);
                    assertThat(dataSource.getConnectionTimeout()).isEqualTo(3000L);
                    assertThat(dataSource.isReadOnly()).isTrue();
                });
    }

    @Test
    void shouldNotPreventDynamicDataSourceAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DynamicDataSourceAutoConfiguration.class))
                .withUserConfiguration(BpmFormDataSourceConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.dynamic.primary=master",
                        "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:primary;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.dynamic.datasource.master.username=sa",
                        "spring.datasource.dynamic.datasource.master.password=",
                        "spring.datasource.dynamic.datasource.master.driver-class-name=org.h2.Driver",
                        "bpm.form-data-source.jdbc.url=jdbc:h2:mem:bpm-form-auto-config;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "bpm.form-data-source.jdbc.username=sa",
                        "bpm.form-data-source.jdbc.password=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(DynamicRoutingDataSource.class);
                    assertThat(context).hasSingleBean(BpmFormReadOnlyPool.class);
                    assertThat(context).hasSingleBean(BpmFormDataSourceExecutor.class);
                });
    }

}
