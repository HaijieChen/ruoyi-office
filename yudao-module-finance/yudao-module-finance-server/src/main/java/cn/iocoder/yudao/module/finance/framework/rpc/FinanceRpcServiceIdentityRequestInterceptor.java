package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityTokens;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentitySecretValidator;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import lombok.RequiredArgsConstructor;

/**
 * EXP-87 F3：仅 privileged BPM create-by-business 附带 Finance 服务身份 HMAC。
 * <p>
 * 仅通过 {@link FinanceBpmProcessInstanceApi}{@code @FeignClient(configuration=)} 挂载；
 * method+path <strong>精确匹配</strong>；audience 绑定 BPM 目标服务名，防跨服务/跨路径重放。
 */
@RequiredArgsConstructor
public class FinanceRpcServiceIdentityRequestInterceptor implements RequestInterceptor {

    private final RpcServiceIdentityProperties properties;

    @Override
    public void apply(RequestTemplate template) {
        if (Boolean.FALSE.equals(properties.getEnabled())) {
            return;
        }
        if (!isPrivilegedBpmCreateByBusiness(template)) {
            return;
        }
        String targetName = resolveTargetName(template);
        if (!RpcServiceIdentityConstants.BPM_SERVER.equals(targetName)) {
            // 目标不是 bpm-server：不签发（即使路径碰巧相同）
            return;
        }
        String secret = RpcServiceIdentitySecretValidator.requireStrongSecretOrThrow(properties.getSecret());
        String serviceName = RpcServiceIdentityConstants.FINANCE_SERVER;
        String audience = RpcServiceIdentityConstants.audience(
                RpcServiceIdentityConstants.METHOD_BPM_CREATE_BY_BUSINESS,
                RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS,
                RpcServiceIdentityConstants.BPM_SERVER);
        String token = RpcServiceIdentityTokens.sign(serviceName, audience, secret);
        template.header(RpcServiceIdentityConstants.HEADER_SERVICE_NAME, serviceName);
        template.header(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
    }

    /**
     * method + 规范化 path 精确相等，且目标为 bpm-server。
     */
    static boolean isPrivilegedBpmCreateByBusiness(RequestTemplate template) {
        if (template == null) {
            return false;
        }
        String method = template.method();
        String path = resolvePath(template);
        if (!RpcServiceIdentityConstants.isExactPrivilegedCreateByBusiness(method, path)) {
            return false;
        }
        return RpcServiceIdentityConstants.BPM_SERVER.equals(resolveTargetName(template));
    }

    static String resolveTargetName(RequestTemplate template) {
        if (template == null) {
            return null;
        }
        Target<?> target = template.feignTarget();
        if (target == null) {
            return null;
        }
        String name = target.name();
        return StrUtil.isBlank(name) ? null : name.trim();
    }

    static String resolvePath(RequestTemplate template) {
        String path = template.path();
        if (StrUtil.isBlank(path)) {
            path = template.url();
        }
        return RpcServiceIdentityConstants.normalizePath(path);
    }
}
