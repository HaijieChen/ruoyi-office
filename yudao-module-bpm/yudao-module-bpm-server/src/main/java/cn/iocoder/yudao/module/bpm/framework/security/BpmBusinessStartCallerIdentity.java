package cn.iocoder.yudao.module.bpm.framework.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityTokens;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentitySecretValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * EXP-87 G1：在 callTrusted 前校验可验证的 Finance 服务身份。
 * <p>
 * 校验来源（任一成立）：
 * 1. SecurityContext 已有 {@link RpcServiceIdentityConstants#AUTHORITY_FINANCE_SERVER}
 *    （由 Filter 在 HMAC 校验通过后写入）；
 * 2. 当前 HTTP 请求 Header 携带有效 Finance HMAC（防御 Filter 未挂载时的同进程调用）。
 */
public final class BpmBusinessStartCallerIdentity {

    private BpmBusinessStartCallerIdentity() {
    }

    public static boolean isVerifiedFinanceCaller(RpcServiceIdentityProperties properties) {
        if (properties != null && Boolean.FALSE.equals(properties.getEnabled())) {
            return false; // enabled=false 时 privileged 路径整体关闭，不得 fail-open
        }
        if (hasFinanceAuthority()) {
            return true;
        }
        return verifyRequestHeaders(properties);
    }

    public static boolean hasFinanceAuthority() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static boolean verifyRequestHeaders(RpcServiceIdentityProperties properties) {
        if (properties == null || !RpcServiceIdentitySecretValidator.isStrongSecret(properties.getSecret())) {
            return false; // F1：弱/空密钥一律拒绝校验通过（fail-closed）
        }
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return false;
        }
        HttpServletRequest request = attrs.getRequest();
        return verifyHeaders(
                request.getHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME),
                request.getHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN),
                properties.getSecret().trim());
    }

    public static boolean verifyHeaders(String serviceName, String token, String secret) {
        if (!RpcServiceIdentityConstants.FINANCE_SERVER.equals(StrUtil.trim(serviceName))) {
            return false;
        }
        if (!RpcServiceIdentitySecretValidator.isStrongSecret(secret)) {
            return false;
        }
        return RpcServiceIdentityTokens.verify(serviceName.trim(), token, secret.trim());
    }
}
