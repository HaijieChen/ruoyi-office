package cn.iocoder.yudao.framework.datasource.config;

import cn.iocoder.yudao.framework.datasource.core.DruidPoolHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
public class YudaoDruidPoolHealthAutoConfiguration {

    @Bean
    public DruidPoolHealthIndicator druidPoolHealthIndicator(DataSource dataSource) {
        return new DruidPoolHealthIndicator(dataSource);
    }

}
