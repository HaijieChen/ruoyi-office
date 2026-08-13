package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityTokens;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentitySecretValidator;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

/**
 * EXP-87 G1/F2：仅 privileged BPM 路径附带 Finance 服务身份 HMAC。
 * <p>
 * - 仅应通过 {@link FinanceBpmProcessInstanceFeignConfiguration} 挂到 {@code BpmProcessInstanceApi}，
 *   <strong>不得</strong>注册为父 ApplicationContext 的全局 {@code RequestInterceptor} Bean。
 * - 仅当请求路径为 create-by-business 时写 Header；签名绑定 audience，防跨路由重放。
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
            // F2：非 privileged 路径（含其他 Feign 若误挂本拦截器）一律不附身份头
            return;
        }
        String secret = RpcServiceIdentitySecretValidator.requireStrongSecretOrThrow(properties.getSecret());
        String serviceName = RpcServiceIdentityConstants.FINANCE_SERVER;
        String audience = RpcServiceIdentityConstants.AUDIENCE_BPM_CREATE_BY_BUSINESS;
        String token = RpcServiceIdentityTokens.sign(serviceName, audience, secret);
        template.header(RpcServiceIdentityConstants.HEADER_SERVICE_NAME, serviceName);
        template.header(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN, token);
    }

    /**
     * 是否 create-by-business privileged 调用。
     */
    static boolean isPrivilegedBpmCreateByBusiness(RequestTemplate template) {
        if (template == null) {
            return false;
        }
        String method = template.method();
        if (method == null || !"POST".equalsIgnoreCase(method)) {
            return false;
        }
        String path = resolvePath(template);
        return path != null && path.contains(RpcServiceIdentityConstants.PATH_BPM_CREATE_BY_BUSINESS);
    }

    private static String resolvePath(RequestTemplate template) {
        // Feign: path() 可能是相对 path；url() 可能含 query
        String path = template.path();
        if (StrUtil.isBlank(path)) {
            path = template.url();
        }
        if (path == null) {
            return null;
        }
        int q = path.indexOf('?');
        return q >= 0 ? path.substring(0, q) : path;
    }
}
