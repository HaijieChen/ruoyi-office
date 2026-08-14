package cn.iocoder.yudao.module.bpm.framework.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentitySecretValidator;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * EXP-87 G1：create-by-business 入口服务身份过滤器。
 * <p>
 * 匿名 / 伪造 header / 非 Finance 调用方 → 401/403，不进入业务与 callTrusted。
 * 校验通过 → 写入 {@link RpcServiceIdentityConstants#AUTHORITY_FINANCE_SERVER}。
 */
@Slf4j
@RequiredArgsConstructor
public class BpmBusinessStartIdentityFilter extends OncePerRequestFilter {

    static final String PATH_SUFFIX = "/process-instance/create-by-business";

    private final RpcServiceIdentityProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.contains(ApiConstants.PREFIX + PATH_SUFFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (Boolean.FALSE.equals(properties.getEnabled())) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                    "可信业务启动通道已关闭");
            return;
        }
        // F1：运行时再次 fail-closed（防御配置热更/错误注入弱密钥）
        if (!RpcServiceIdentitySecretValidator.isStrongSecret(properties.getSecret())) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                    "RPC 服务身份密钥未配置或不合规，create-by-business 拒绝服务");
            return;
        }
        String serviceName = request.getHeader(RpcServiceIdentityConstants.HEADER_SERVICE_NAME);
        String token = request.getHeader(RpcServiceIdentityConstants.HEADER_SERVICE_TOKEN);
        if (StrUtil.isBlank(serviceName) || StrUtil.isBlank(token)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    GlobalErrorCodeConstants.UNAUTHORIZED.getCode(),
                    "缺少 RPC 服务身份，禁止调用 create-by-business");
            return;
        }
        if (!BpmBusinessStartCallerIdentity.verifyHeaders(serviceName, token, properties.getSecret())) {
            // 有 header 但伪造/非 Finance → 403
            log.warn("[BpmBusinessStartIdentityFilter] rejected caller serviceName={}", serviceName);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                    "非 Finance 服务身份或签名无效，禁止调用 create-by-business");
            return;
        }
        // 写入 SecurityContext，供 hasAuthority 与服务层二次校验
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                RpcServiceIdentityConstants.FINANCE_SERVER,
                "N/A",
                List.of(new SimpleGrantedAuthority(RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 仅清理我们写入的服务身份，避免污染同线程后续
            AuthenticationCleanup.clearIfFinanceService();
        }
    }

    private static void writeJson(HttpServletResponse response, int httpStatus, int code, String msg)
            throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJsonString(CommonResult.error(code, msg)));
    }

    /** 内部清理辅助 */
    static final class AuthenticationCleanup {
        private AuthenticationCleanup() {
        }

        static void clearIfFinanceService() {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null
                    && RpcServiceIdentityConstants.FINANCE_SERVER.equals(String.valueOf(auth.getPrincipal()))) {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
