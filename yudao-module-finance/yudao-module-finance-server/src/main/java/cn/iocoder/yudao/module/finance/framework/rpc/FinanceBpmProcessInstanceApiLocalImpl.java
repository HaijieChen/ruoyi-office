package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentitySecretValidator;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * EXP-87：单体 {@code yudao-server} 下的 Finance BPM API 本地适配。
 * <p>
 * 根因：{@code yudao-server} 排除 {@code spring-cloud-starter-openfeign}，
 * Feign 客户端 {@link FinanceBpmProcessInstanceApi} 不会注册；而 CRM 等模块注入的
 * {@link BpmProcessInstanceApi} 可由同 JVM 的 {@code @RestController} 实现满足。
 * 本类提供同进程委托，并在 create-by-business 前写入 Finance 服务身份
 * （等价于 Feign HMAC 过滤器通过后的 SecurityContext 权限）。
 */
public class FinanceBpmProcessInstanceApiLocalImpl implements FinanceBpmProcessInstanceApi {

    private final BpmProcessInstanceApi delegate;
    private final RpcServiceIdentityProperties identityProperties;

    public FinanceBpmProcessInstanceApiLocalImpl(BpmProcessInstanceApi delegate,
                                                 RpcServiceIdentityProperties identityProperties) {
        this.delegate = delegate;
        this.identityProperties = identityProperties;
    }

    @Override
    public CommonResult<String> createProcessInstance(Long userId, BpmProcessInstanceCreateReqDTO reqDTO) {
        return delegate.createProcessInstance(userId, reqDTO);
    }

    @Override
    public CommonResult<String> createProcessInstanceByBusiness(Long userId,
                                                                BpmProcessInstanceCreateReqDTO reqDTO) {
        // 与 Feign interceptor 一致：enabled + 强密钥 fail-closed；再模拟 Filter 写入 authority
        if (identityProperties == null || Boolean.FALSE.equals(identityProperties.getEnabled())) {
            throw new IllegalStateException(
                    "yudao.rpc.service-identity.enabled=false：privileged create-by-business 通道已关闭");
        }
        RpcServiceIdentitySecretValidator.requireStrongSecretOrThrow(identityProperties.getSecret());
        return withFinanceAuthority(() -> delegate.createProcessInstanceByBusiness(userId, reqDTO));
    }

    @Override
    public CommonResult<String> submitProcessInstance(Long userId, BpmProcessInstanceCreateReqDTO reqDTO) {
        return delegate.submitProcessInstance(userId, reqDTO);
    }

    @Override
    public CommonResult<Boolean> cancelProcessInstanceByStartUser(
            Long userId, String processInstanceId, String reason,
            Collection<String> forbiddenTaskDefinitionKeys) {
        return delegate.cancelProcessInstanceByStartUser(
                userId, processInstanceId, reason, forbiddenTaskDefinitionKeys);
    }

    @Override
    public CommonResult<Boolean> returnCurrentTaskToStartUserTask(Long userId, String taskId, String reason) {
        return delegate.returnCurrentTaskToStartUserTask(userId, taskId, reason);
    }

    @Override
    public CommonResult<Boolean> canAccessRelated(Long userId, String processInstanceId) {
        return delegate.canAccessRelated(userId, processInstanceId);
    }

    @Override
    public CommonResult<java.util.Set<String>> listSharedInstanceIds(Long userId) {
        return delegate.listSharedInstanceIds(userId);
    }

    private <T> T withFinanceAuthority(java.util.function.Supplier<T> action) {
        Authentication original = SecurityContextHolder.getContext().getAuthentication();
        try {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (original != null && original.getAuthorities() != null) {
                authorities.addAll(original.getAuthorities());
            }
            boolean has = authorities.stream().anyMatch(a ->
                    RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER.equals(a.getAuthority()));
            if (!has) {
                authorities.add(new SimpleGrantedAuthority(
                        RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER));
            }
            Object principal = original != null ? original.getPrincipal() : "finance-server-local";
            Object credentials = original != null ? original.getCredentials() : null;
            Authentication elevated = new UsernamePasswordAuthenticationToken(
                    principal, credentials, authorities);
            SecurityContextHolder.getContext().setAuthentication(elevated);
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(original);
        }
    }
}
