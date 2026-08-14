package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.config.FinanceBpmLocalApiConfiguration;
import cn.iocoder.yudao.module.finance.framework.rpc.config.RpcConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * FINAL-6C6F-01：显式 local 模式时，即使 OpenFeign 在 CP 且扫描了 RpcConfiguration，也只注册 Local。
 */
@SpringBootTest(classes = FinanceBpmLocalForcedModeCoexistenceSpringTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.application.name=yudao-server",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.cloud.openfeign.client.config.default.url=http://127.0.0.1:9",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "yudao.rpc.finance-bpm.api-mode=local",
        "yudao.rpc.service-identity.enabled=true",
        "yudao.rpc.service-identity.secret=prod-injected-rpc-service-identity-key-9f3a"
})
class FinanceBpmLocalForcedModeCoexistenceSpringTest {

    @SpringBootConfiguration
    @Import({FinanceBpmLocalApiConfiguration.class, RpcConfiguration.class})
    @ImportAutoConfiguration(FeignAutoConfiguration.class)
    @EnableConfigurationProperties(RpcServiceIdentityProperties.class)
    static class TestApp {
        @Bean
        @Primary
        BpmProcessInstanceApi bpmProcessInstanceApiRestController() {
            return mock(BpmProcessInstanceApi.class);
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("bpmProcessInstanceApiRestController")
    private BpmProcessInstanceApi baseline;

    @Test
    void forcedLocalModeSkipsFeignAndRegistersSingleLocalBean() {
        Map<String, FinanceBpmProcessInstanceApi> financeBeans =
                applicationContext.getBeansOfType(FinanceBpmProcessInstanceApi.class);
        assertEquals(1, financeBeans.size(), "forced local: single Finance bean: " + financeBeans.keySet());
        FinanceBpmProcessInstanceApi finance = applicationContext.getBean(FinanceBpmProcessInstanceApi.class);
        assertTrue(finance instanceof FinanceBpmProcessInstanceApiLocalImpl);

        BpmProcessInstanceApi parent = applicationContext.getBean(BpmProcessInstanceApi.class);
        assertSame(baseline, parent, "parent type must stay baseline under forced local");
        assertFalse(parent instanceof FinanceBpmProcessInstanceApi);
    }
}
