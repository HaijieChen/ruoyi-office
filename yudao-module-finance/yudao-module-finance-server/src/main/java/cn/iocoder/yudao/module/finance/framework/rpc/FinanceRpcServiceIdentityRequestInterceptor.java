package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityTokens;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentitySecretValidator;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

/**
 * EXP-87 G1：Finance 出站 Feign 携带可验证的服务身份（HMAC），
 * 供 BPM create-by-business 在 callTrusted 前校验。
 * <p>
 * F1：无强密钥时拒绝签发（fail-closed），不使用源码默认 secret。
 */
@RequiredArgsConstructor
public class FinanceRpcServiceIdentityRequestInterceptor implements RequestInterceptor {

    private final RpcServiceIdentityProperties properties;

    @Override
    public void apply(RequestTemplate template) {
        if (Boolean.FALSE.equals(properties.getEnabled())) {
            // 通道关闭：不附带身份，BPM 侧将拒绝 create-by-business
            return;
        }
        String secret = RpcServiceIdentitySecretValidator.requireStrongSecretOrThrow(properties.getSecret());
        String serviceName = RpcServiceIdentityConstants.FINANCE_SERVER;
        String token = RpcServiceIdentityTokens.sign(serviceName, secret);
        template.header(RpcServiceIdentityConstants.HEADER_SERVICE_NAME, serviceName);
        template.header(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
    }
}
