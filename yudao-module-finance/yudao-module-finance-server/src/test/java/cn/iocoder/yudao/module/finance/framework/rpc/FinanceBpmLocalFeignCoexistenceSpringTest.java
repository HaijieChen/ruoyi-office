package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.config.FinanceBpmLocalApiConfiguration;
import cn.iocoder.yudao.module.finance.framework.rpc.config.RpcConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.ClassUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FINAL-6C6F-01：同时扫描 Local + Rpc 生产配置时，模式互斥且无双 primary。
 * <p>
 * finance-server 测试 CP 含 OpenFeign；默认 auto → 仅 Feign。
 */
@SpringBootTest(classes = FinanceBpmLocalFeignCoexistenceSpringTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.application.name=finance-server",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.cloud.openfeign.client.config.default.url=http://127.0.0.1:9",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        // 显式 auto：OpenFeign 在 CP → feign only
        "yudao.rpc.finance-bpm.api-mode=auto",
        "yudao.rpc.service-identity.enabled=true",
        "yudao.rpc.service-identity.secret=prod-injected-rpc-service-identity-key-9f3a"
})
class FinanceBpmLocalFeignCoexistenceSpringTest {

    @SpringBootConfiguration
    @Import({FinanceBpmLocalApiConfiguration.class, RpcConfiguration.class})
    @ImportAutoConfiguration(FeignAutoConfiguration.class)
    @EnableConfigurationProperties(RpcServiceIdentityProperties.class)
    static class TestApp {
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void openFeignOnClasspathAutoModeRegistersOnlyFeignFinanceBean() {
        assertTrue(ClassUtils.isPresent("org.springframework.cloud.openfeign.FeignClient",
                getClass().getClassLoader()));

        Map<String, FinanceBpmProcessInstanceApi> financeBeans =
                applicationContext.getBeansOfType(FinanceBpmProcessInstanceApi.class);
        assertEquals(1, financeBeans.size(),
                "coexistence scan must not dual-register Finance beans: " + financeBeans.keySet());
        assertFalse(financeBeans.values().stream().anyMatch(b -> b instanceof FinanceBpmProcessInstanceApiLocalImpl),
                "auto+OpenFeign must not register LocalImpl");

        FinanceBpmProcessInstanceApi finance = applicationContext.getBean(FinanceBpmProcessInstanceApi.class);
        assertNotNull(finance);
        // Feign proxy, not local adapter
        assertFalse(finance instanceof FinanceBpmProcessInstanceApiLocalImpl);
    }

    @Test
    void exactAndParentTypeResolveWithoutNoUniqueBeanDefinition() {
        assertDoesNotThrow(() -> applicationContext.getBean(FinanceBpmProcessInstanceApi.class));
        // parent type: Finance Feign primary=true may win; must still be unique
        assertDoesNotThrow(() -> applicationContext.getBean(BpmProcessInstanceApi.class));
        BpmProcessInstanceApi parent = applicationContext.getBean(BpmProcessInstanceApi.class);
        assertSame(applicationContext.getBean(FinanceBpmProcessInstanceApi.class), parent,
                "with only Finance Feign client, parent type should be the same primary Finance client");
    }
}
