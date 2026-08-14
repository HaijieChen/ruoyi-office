package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import feign.RequestInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-87 F3：真实 OpenFeign 多命名上下文 — 身份拦截器仅在
 * financeBpmProcessInstanceApi 上下文；system-server 上下文不含该拦截器。
 * <p>
 * 不使用 {@code defaultConfiguration}；仅 {@code @FeignClient(configuration=)}。
 */
@SpringBootTest(classes = FinanceFeignClientIdentityScopeSpringTest.TestApp.class,
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
        "yudao.rpc.service-identity.enabled=true",
        "yudao.rpc.service-identity.secret=prod-injected-rpc-service-identity-key-9f3a"
})
class FinanceFeignClientIdentityScopeSpringTest {

    /**
     * 精简上下文：只导入 Feign 自动配置 + 两个客户端，避免 yudao 其它 Feign 自动装配冲突。
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration({FeignAutoConfiguration.class})
    @EnableFeignClients(clients = {
            FinanceBpmProcessInstanceApi.class,
            AdminUserApi.class
    })
    @EnableConfigurationProperties(RpcServiceIdentityProperties.class)
    static class TestApp {
        // secret 由 TestPropertySource 注入；不另注册 Bean，避免 NoUniqueBeanDefinition
    }

    @Autowired
    private FeignClientFactory feignClientFactory;

    @Test
    void onlyFinanceBpmNamedContextHasIdentityInterceptor() {
        Map<String, RequestInterceptor> bpmInterceptors =
                feignClientFactory.getInstancesWithoutAncestors(
                        "financeBpmProcessInstanceApi", RequestInterceptor.class);
        assertNotNull(bpmInterceptors);
        assertTrue(bpmInterceptors.values().stream()
                        .anyMatch(i -> i instanceof FinanceRpcServiceIdentityRequestInterceptor),
                "BPM finance context must contain identity interceptor, got: " + bpmInterceptors.keySet()
                        + " / " + bpmInterceptors.values().stream().map(Object::getClass).toList());

        // AdminUserApi 默认 contextId = name = system-server
        Map<String, RequestInterceptor> systemInterceptors =
                feignClientFactory.getInstancesWithoutAncestors(
                        "system-server", RequestInterceptor.class);
        assertTrue(systemInterceptors == null || systemInterceptors.isEmpty()
                        || systemInterceptors.values().stream()
                        .noneMatch(i -> i instanceof FinanceRpcServiceIdentityRequestInterceptor),
                "System context must NOT contain Finance identity interceptor: " + systemInterceptors);
    }

    @Test
    void annotations_proveClientScopedConfiguration() {
        org.springframework.cloud.openfeign.FeignClient bpm =
                FinanceBpmProcessInstanceApi.class.getAnnotation(
                        org.springframework.cloud.openfeign.FeignClient.class);
        org.springframework.cloud.openfeign.FeignClient system =
                AdminUserApi.class.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class);
        assertTrue(Arrays.asList(bpm.configuration())
                .contains(FinanceBpmProcessInstanceFeignConfiguration.class));
        assertFalse(Arrays.asList(system.configuration())
                .contains(FinanceBpmProcessInstanceFeignConfiguration.class));
        assertEquals("financeBpmProcessInstanceApi", bpm.contextId());
    }
}
