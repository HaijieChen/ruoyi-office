package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityTokens;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-87 F3：精确路径 + BPM 目标绑定 + 非 privileged 无头。
 */
class FinanceRpcServiceIdentityRequestInterceptorTest {

    private static final String STRONG_SECRET = "prod-injected-rpc-service-identity-key-9f3a";

    private static FinanceRpcServiceIdentityRequestInterceptor interceptor() {
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret(STRONG_SECRET);
        return new FinanceRpcServiceIdentityRequestInterceptor(props);
    }

    private static RequestTemplate template(String method, String path, String targetName) {
        RequestTemplate t = new RequestTemplate();
        t.method(method);
        t.uri(path);
        t.feignTarget(new Target.HardCodedTarget<>(Object.class, targetName, "http://" + targetName));
        return t;
    }

    @Test
    void createByBusiness_onBpmTarget_attachesAudienceBoundIdentity() {
        RequestTemplate template = template("POST",
                RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS,
                RpcServiceIdentityConstants.BPM_SERVER);
        interceptor().apply(template);

        Collection<String> names = template.headers().get(RpcServiceIdentityConstants.HEADER_SERVICE_NAME);
        Collection<String> tokens = template.headers().get(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN);
        assertNotNull(names);
        assertNotNull(tokens);
        String name = names.iterator().next();
        String token = tokens.iterator().next();
        assertEquals(RpcServiceIdentityConstants.FINANCE_SERVER, name);
        assertTrue(RpcServiceIdentityTokens.verify(name,
                RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS, token, STRONG_SECRET));
    }

    @Test
    void createByBusiness_onWrongTarget_noIdentityHeaders() {
        RequestTemplate template = template("POST",
                RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS,
                "system-server");
        interceptor().apply(template);
        assertNoIdentityHeaders(template);
    }

    @Test
    void pathPrefixLookalike_noIdentityHeaders() {
        // contains 会误匹配，精确匹配必须拒绝
        RequestTemplate template = template("POST",
                "/rpc-api/bpm/process-instance/create-by-business/extra",
                RpcServiceIdentityConstants.BPM_SERVER);
        interceptor().apply(template);
        assertNoIdentityHeaders(template);
    }

    @Test
    void genericBpmCreate_noIdentityHeaders() {
        RequestTemplate template = template("POST", "/rpc-api/bpm/process-instance/create",
                RpcServiceIdentityConstants.BPM_SERVER);
        interceptor().apply(template);
        assertNoIdentityHeaders(template);
    }

    @Test
    void systemLikePath_noIdentityHeaders() {
        RequestTemplate template = template("POST", "/rpc-api/system/user/get", "system-server");
        interceptor().apply(template);
        assertNoIdentityHeaders(template);
    }

    @Test
    void tokenForOtherTarget_cannotAuthorizeCreateByBusiness() {
        String other = RpcServiceIdentityConstants.audience("POST",
                RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS, "system-server");
        String replay = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, other, STRONG_SECRET);
        assertFalse(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER,
                RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS,
                replay, STRONG_SECRET));
    }

    @Test
    void tokenForOtherPath_cannotAuthorizeCreateByBusiness() {
        String other = RpcServiceIdentityConstants.audience("POST",
                "/rpc-api/bpm/process-instance/create", RpcServiceIdentityConstants.BPM_SERVER);
        String replay = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, other, STRONG_SECRET);
        assertFalse(RpcServiceIdentityTokens.verify(
                RpcServiceIdentityConstants.FINANCE_SERVER,
                RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS,
                replay, STRONG_SECRET));
    }

    @Test
    void apply_withMissingSecret_onPrivilegedPath_failsClosed() {
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret(null);
        FinanceRpcServiceIdentityRequestInterceptor interceptor =
                new FinanceRpcServiceIdentityRequestInterceptor(props);
        RequestTemplate template = template("POST",
                RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS,
                RpcServiceIdentityConstants.BPM_SERVER);
        assertThrows(IllegalStateException.class, () -> interceptor.apply(template));
    }

    @Test
    void feignConfiguration_isNotSpringConfigurationAnnotation() {
        assertFalse(FinanceBpmProcessInstanceFeignConfiguration.class
                .isAnnotationPresent(org.springframework.context.annotation.Configuration.class));
    }

    @Test
    void financeBpmApi_usesClientConfigurationNotDefaultConfiguration() {
        org.springframework.cloud.openfeign.FeignClient ann =
                FinanceBpmProcessInstanceApi.class.getAnnotation(
                        org.springframework.cloud.openfeign.FeignClient.class);
        assertNotNull(ann);
        assertArrayEquals(new Class<?>[]{FinanceBpmProcessInstanceFeignConfiguration.class},
                ann.configuration());
        // EnableFeignClients 不得带 defaultConfiguration
        org.springframework.cloud.openfeign.EnableFeignClients enable =
                cn.iocoder.yudao.module.finance.framework.rpc.config.RpcConfiguration.class
                        .getAnnotation(org.springframework.cloud.openfeign.EnableFeignClients.class);
        assertNotNull(enable);
        assertEquals(0, enable.defaultConfiguration().length,
                "must not use @EnableFeignClients(defaultConfiguration=…)");
        assertArrayEquals(new Class<?>[]{FinanceBpmProcessInstanceApi.class}, enable.clients());
    }

    private static void assertNoIdentityHeaders(RequestTemplate template) {
        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.get(RpcServiceIdentityConstants.HEADER_SERVICE_NAME) == null
                || headers.get(RpcServiceIdentityConstants.HEADER_SERVICE_NAME).isEmpty());
        assertTrue(headers.get(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN) == null
                || headers.get(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN).isEmpty());
    }
}
