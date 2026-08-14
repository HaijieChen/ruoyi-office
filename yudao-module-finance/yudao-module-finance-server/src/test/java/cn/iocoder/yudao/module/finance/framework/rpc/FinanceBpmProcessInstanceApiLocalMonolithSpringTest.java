package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.finance.framework.rpc.config.FinanceBpmLocalApiConfiguration;
import cn.iocoder.yudao.module.finance.service.invoice.FinanceInvoiceApplicationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * EXP-87：单体无 OpenFeign 时，本地 {@link FinanceBpmProcessInstanceApi} 必须可注入
 * （复现 test 环境 APPLICATION FAILED：缺少 FinanceBpmProcessInstanceApi Bean）。
 */
@SpringBootTest(classes = FinanceBpmProcessInstanceApiLocalMonolithSpringTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.application.name=yudao-server",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "yudao.rpc.service-identity.enabled=true",
        "yudao.rpc.service-identity.secret=prod-injected-rpc-service-identity-key-9f3a"
})
class FinanceBpmProcessInstanceApiLocalMonolithSpringTest {

    @SpringBootConfiguration
    @Import(FinanceBpmLocalApiConfiguration.class)
    @EnableConfigurationProperties(RpcServiceIdentityProperties.class)
    static class TestApp {
        /** 模拟同 JVM 的 BpmProcessInstanceApiImpl（@RestController） */
        @Bean
        BpmProcessInstanceApi bpmProcessInstanceApiRestController() {
            return mock(BpmProcessInstanceApi.class);
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FinanceBpmProcessInstanceApi financeBpmProcessInstanceApi;

    /** 同 JVM RestController 模拟；勿注入父类型（@Primary 本地适配器会抢占） */
    @Autowired
    @Qualifier("bpmProcessInstanceApiRestController")
    private BpmProcessInstanceApi bpmProcessInstanceApi;

    @Test
    void monolithContextProvidesFinanceBpmProcessInstanceApiBean() {
        assertNotNull(applicationContext.getBean(FinanceBpmProcessInstanceApi.class));
        assertTrue(financeBpmProcessInstanceApi instanceof FinanceBpmProcessInstanceApiLocalImpl);
    }

    @Test
    void financeInvoiceConstructorCanResolveFinanceBpmProcessInstanceApi() {
        Constructor<?>[] ctors = FinanceInvoiceApplicationServiceImpl.class.getConstructors();
        assertEquals(1, ctors.length);
        Class<?>[] params = ctors[0].getParameterTypes();
        assertTrue(Arrays.asList(params).contains(FinanceBpmProcessInstanceApi.class),
                "Invoice ctor must require FinanceBpmProcessInstanceApi: " + Arrays.toString(params));
        // 容器中类型可解析
        assertDoesNotThrow(() -> applicationContext.getBean(FinanceBpmProcessInstanceApi.class));
    }

    @Test
    void createByBusinessElevatesFinanceAuthorityForDelegate() {
        AtomicReference<Boolean> sawAuthority = new AtomicReference<>(false);
        when(bpmProcessInstanceApi.createProcessInstanceByBusiness(anyLong(), any()))
                .thenAnswer(inv -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    boolean has = auth != null && auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .anyMatch(RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER::equals);
                    sawAuthority.set(has);
                    return CommonResult.success("pi-1");
                });

        BpmProcessInstanceCreateReqDTO dto = new BpmProcessInstanceCreateReqDTO();
        CommonResult<String> result = financeBpmProcessInstanceApi.createProcessInstanceByBusiness(1L, dto);
        assertEquals("pi-1", result.getData());
        assertTrue(Boolean.TRUE.equals(sawAuthority.get()),
                "local adapter must elevate FINANCE authority before create-by-business");
        verify(bpmProcessInstanceApi).createProcessInstanceByBusiness(eq(1L), any());
    }
}
