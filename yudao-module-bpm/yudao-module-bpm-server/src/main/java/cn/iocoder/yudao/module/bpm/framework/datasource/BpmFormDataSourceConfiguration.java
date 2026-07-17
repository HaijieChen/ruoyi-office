package cn.iocoder.yudao.module.bpm.framework.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.time.Duration;

/** Spring wiring for the isolated form data-source runtime. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BpmFormDataSourceProperties.class)
public class BpmFormDataSourceConfiguration {

    @Bean
    public BpmFormDataSourceSqlValidator bpmFormDataSourceSqlValidator() {
        return new BpmFormDataSourceSqlValidator();
    }

    @Bean
    public BpmFormDataSourceContextResolver bpmFormDataSourceContextResolver() {
        return new BpmFormDataSourceContextResolver();
    }

    @Bean(destroyMethod = "close")
    @Conditional(ReadOnlyJdbcConfiguredCondition.class)
    public BpmFormReadOnlyPool bpmFormReadOnlyPool(BpmFormDataSourceProperties properties) {
        BpmFormDataSourceProperties.Jdbc jdbc = properties.getJdbc();
        HikariConfig config = new HikariConfig();
        config.setPoolName("bpm-form-read-only");
        config.setJdbcUrl(jdbc.getUrl());
        config.setUsername(jdbc.getUsername());
        config.setPassword(jdbc.getPassword());
        config.setMaximumPoolSize(Math.min(2, Math.max(1, jdbc.getMaximumPoolSize())));
        config.setConnectionTimeout(Math.min(3000L, Math.max(250L, jdbc.getConnectionTimeoutMs())));
        config.setReadOnly(true);
        config.setAutoCommit(true);
        return new BpmFormReadOnlyPool(new HikariDataSource(config));
    }

    @Bean
    @Conditional(ReadOnlyJdbcConfiguredCondition.class)
    public BpmFormDataSourceExecutor bpmFormDataSourceExecutor(BpmFormReadOnlyPool pool) {
        return new BpmFormDataSourceExecutor(pool.getDataSource());
    }

    @Bean
    public HttpClient bpmFormDataSourceHttpClient(BpmFormDataSourceProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getApi().getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    static final class ReadOnlyJdbcConfiguredCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return StringUtils.hasText(context.getEnvironment().getProperty("bpm.form-data-source.jdbc.url"));
        }
    }

}
