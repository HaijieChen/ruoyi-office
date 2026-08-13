package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityTokens;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

/**
 * EXP-87 G1：Finance 出站 Feign 携带可验证的服务身份（HMAC），
 * 供 BPM create-by-business 在 callTrusted 前校验。
 */
@RequiredArgsConstructor
public class FinanceRpcServiceIdentityRequestInterceptor implements RequestInterceptor {

    private final RpcServiceIdentityProperties properties;

    @Override
    public void apply(RequestTemplate template) {
        String secret = properties.getSecret();
        String serviceName = RpcServiceIdentityConstants.FINANCE_SERVER;
        String token = RpcServiceIdentityTokens.sign(serviceName, secret);
        template.header(RpcServiceIdentityConstants.HEADER_SERVICE_NAME, serviceName);
        template.header(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
    }
}
