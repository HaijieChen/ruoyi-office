package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityTokens;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finance 出站 Feign 必须附带可验证的 finance-server HMAC。
 */
class FinanceRpcServiceIdentityRequestInterceptorTest {

    private static final String STRONG_SECRET = "prod-injected-rpc-service-identity-key-9f3a";

    @Test
    void apply_attachesVerifiableFinanceIdentity() {
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret(STRONG_SECRET);
        FinanceRpcServiceIdentityRequestInterceptor interceptor =
                new FinanceRpcServiceIdentityRequestInterceptor(props);
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        Collection<String> names = template.headers().get(RpcServiceIdentityConstants.HEADER_SERVICE_NAME);
        Collection<String> tokens = template.headers().get(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN);
        assertNotNull(names);
        assertNotNull(tokens);
        String name = names.iterator().next();
        String token = tokens.iterator().next();
        assertEquals(RpcServiceIdentityConstants.FINANCE_SERVER, name);
        assertTrue(RpcServiceIdentityTokens.verify(name, token, STRONG_SECRET));
    }

    @Test
    void apply_withMissingSecret_failsClosed() {
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret(null);
        FinanceRpcServiceIdentityRequestInterceptor interceptor =
                new FinanceRpcServiceIdentityRequestInterceptor(props);
        assertThrows(IllegalStateException.class, () -> interceptor.apply(new RequestTemplate()));
    }

    @Test
    void apply_withBlacklistedDevDefault_failsClosed() {
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret("yudao-rpc-service-identity-dev-only");
        FinanceRpcServiceIdentityRequestInterceptor interceptor =
                new FinanceRpcServiceIdentityRequestInterceptor(props);
        assertThrows(IllegalStateException.class, () -> interceptor.apply(new RequestTemplate()));
    }
}
