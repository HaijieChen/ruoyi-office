package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.service.payment.FinancePaymentApplicationServiceImpl;
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
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-87 F4：生产组合最小复现 — 同时注册基线 {@link BpmProcessInstanceApi}
 * 与 Finance 专用 {@link FinanceBpmProcessInstanceApi}；
 * 容器可刷新；Finance Payment 构造注入点解析为带 identity interceptor 的专用客户端。
 */
@SpringBootTest(classes = FinanceBpmDualClientInjectionSpringTest.TestApp.class,
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
        "yudao.rpc.service-identity.enabled=true",
        "yudao.rpc.service-identity.secret=prod-injected-rpc-service-identity-key-9f3a"
})
class FinanceBpmDualClientInjectionSpringTest {

    @SpringBootConfiguration
    @ImportAutoConfiguration({FeignAutoConfiguration.class})
    @EnableFeignClients(clients = {
            BpmProcessInstanceApi.class,          // CRM 等模块基线客户端（primary=false）
            FinanceBpmProcessInstanceApi.class    // Finance 专用（primary=true + identity）
    })
    @EnableConfigurationProperties(RpcServiceIdentityProperties.class)
    static class TestApp {
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FeignClientFactory feignClientFactory;

    @Test
    void contextRefreshesWithBothBpmFeignClients() {
        assertNotNull(applicationContext.getBean(BpmProcessInstanceApi.class));
        assertNotNull(applicationContext.getBean(FinanceBpmProcessInstanceApi.class));
        // 父类型注入：因 primary=true 应解析到 Finance 子客户端
        BpmProcessInstanceApi byParentType = applicationContext.getBean(BpmProcessInstanceApi.class);
        assertTrue(byParentType instanceof FinanceBpmProcessInstanceApi
                        || ClassUtils.getUserClass(byParentType).getInterfaces().length > 0,
                "BpmProcessInstanceApi bean should resolve to Finance primary client: "
                        + byParentType.getClass());
        // 更严格：专用 bean 与 primary 父类型 bean 为同一实例
        assertSame(applicationContext.getBean(FinanceBpmProcessInstanceApi.class), byParentType);
    }

    @Test
    void financePaymentConstructorRequiresFinanceBpmProcessInstanceApi() {
        Constructor<?>[] ctors = FinancePaymentApplicationServiceImpl.class.getConstructors();
        assertEquals(1, ctors.length);
        Class<?>[] params = ctors[0].getParameterTypes();
        assertTrue(Arrays.asList(params).contains(FinanceBpmProcessInstanceApi.class),
                "Payment ctor must take FinanceBpmProcessInstanceApi, got: " + Arrays.toString(params));
        assertFalse(Arrays.asList(params).contains(BpmProcessInstanceApi.class)
                        && !Arrays.asList(params).contains(FinanceBpmProcessInstanceApi.class),
                "Payment must not inject bare BpmProcessInstanceApi only");
        // 子类型可赋值给父类型字段，构造参数类型必须是子类型以保证唯一
        long financeApiParams = Arrays.stream(params)
                .filter(p -> FinanceBpmProcessInstanceApi.class.equals(p)).count();
        assertEquals(1, financeApiParams);
    }

    @Test
    void financeBpmContextHasIdentityInterceptor_baselineContextDoesNotShareSameConfig() {
        Map<String, RequestInterceptor> financeCtx =
                feignClientFactory.getInstancesWithoutAncestors(
                        "financeBpmProcessInstanceApi", RequestInterceptor.class);
        assertNotNull(financeCtx);
        assertTrue(financeCtx.values().stream()
                        .anyMatch(i -> i instanceof FinanceRpcServiceIdentityRequestInterceptor),
                "Finance BPM named context must have identity interceptor");

        // 基线 BpmProcessInstanceApi 的 contextId 默认为 name=bpm-server
        Map<String, RequestInterceptor> baselineCtx =
                feignClientFactory.getInstancesWithoutAncestors("bpm-server", RequestInterceptor.class);
        assertTrue(baselineCtx == null || baselineCtx.isEmpty()
                        || baselineCtx.values().stream()
                        .noneMatch(i -> i instanceof FinanceRpcServiceIdentityRequestInterceptor),
                "Baseline bpm-server context must not include Finance identity interceptor: "
                        + baselineCtx);
    }

    @Test
    void feignPrimaryFlags_areCorrect() {
        org.springframework.cloud.openfeign.FeignClient base =
                BpmProcessInstanceApi.class.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class);
        org.springframework.cloud.openfeign.FeignClient finance =
                FinanceBpmProcessInstanceApi.class.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class);
        assertFalse(base.primary(), "baseline BpmProcessInstanceApi must be primary=false");
        assertTrue(finance.primary(), "FinanceBpmProcessInstanceApi must be primary=true");
    }
}
