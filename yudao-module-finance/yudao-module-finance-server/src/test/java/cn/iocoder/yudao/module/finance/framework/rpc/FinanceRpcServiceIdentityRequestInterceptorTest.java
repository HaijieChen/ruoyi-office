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
 * EXP-87 F2：身份拦截器作用域 + 路径限定 + audience 绑定。
 */
class FinanceRpcServiceIdentityRequestInterceptorTest {

    private static final String STRONG_SECRET = "prod-injected-rpc-service-identity-key-9f3a";

    private static FinanceRpcServiceIdentityRequestInterceptor interceptor() {
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret(STRONG_SECRET);
        return new FinanceRpcServiceIdentityRequestInterceptor(props);
    }

    private static RequestTemplate template(String method, String path) {
        RequestTemplate t = new RequestTemplate();
        t.method(method);
        // Feign path 形态
        t.uri(path);
        t.target("http://bpm-server");
        return t;
    }

    @Test
    void createByBusiness_attachesVerifiableAudienceBoundIdentity() {
        RequestTemplate template = template("POST",
                RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS);
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
    void genericBpmCreate_noIdentityHeaders() {
        RequestTemplate template = template("POST", "/rpc-api/bpm/process-instance/create");
        interceptor().apply(template);
        assertNoIdentityHeaders(template);
    }

    @Test
    void systemLikePath_noIdentityHeaders() {
        // 模拟误挂到其他客户端时，也不得附加 privileged 头
        RequestTemplate template = template("POST", "/rpc-api/system/user/get");
        interceptor().apply(template);
        assertNoIdentityHeaders(template);
    }

    @Test
    void infraLikePath_noIdentityHeaders() {
        RequestTemplate template = template("GET", "/rpc-api/infra/file/get");
        interceptor().apply(template);
        assertNoIdentityHeaders(template);
    }

    @Test
    void tokenForOtherRoute_cannotAuthorizeCreateByBusiness() {
        String otherAudience = "POST /rpc-api/bpm/process-instance/create";
        String replay = RpcServiceIdentityTokens.sign(
                RpcServiceIdentityConstants.FINANCE_SERVER, otherAudience, STRONG_SECRET);
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
                RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS);
        assertThrows(IllegalStateException.class, () -> interceptor.apply(template));
    }

    @Test
    void apply_withBlacklistedDevDefault_onPrivilegedPath_failsClosed() {
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret("yudao-rpc-service-identity-dev-only");
        FinanceRpcServiceIdentityRequestInterceptor interceptor =
                new FinanceRpcServiceIdentityRequestInterceptor(props);
        RequestTemplate template = template("POST",
                RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS);
        assertThrows(IllegalStateException.class, () -> interceptor.apply(template));
    }

    @Test
    void apply_withMissingSecret_onNonPrivilegedPath_doesNotThrow() {
        // 非 privileged 路径不读 secret
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret(null);
        FinanceRpcServiceIdentityRequestInterceptor interceptor =
                new FinanceRpcServiceIdentityRequestInterceptor(props);
        RequestTemplate template = template("POST", "/rpc-api/system/user/get");
        assertDoesNotThrow(() -> interceptor.apply(template));
        assertNoIdentityHeaders(template);
    }

    @Test
    void feignConfiguration_isNotSpringConfigurationAnnotation() {
        // 防止被组件扫描为父上下文全局 Bean
        assertFalse(FinanceBpmProcessInstanceFeignConfiguration.class
                .isAnnotationPresent(org.springframework.context.annotation.Configuration.class));
    }

    private static void assertNoIdentityHeaders(RequestTemplate template) {
        Map<String, Collection<String>> headers = template.headers();
        assertTrue(headers.get(RpcServiceIdentityConstants.HEADER_SERVICE_NAME) == null
                || headers.get(RpcServiceIdentityConstants.HEADER_SERVICE_NAME).isEmpty());
        assertTrue(headers.get(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN) == null
                || headers.get(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN).isEmpty());
    }
}
