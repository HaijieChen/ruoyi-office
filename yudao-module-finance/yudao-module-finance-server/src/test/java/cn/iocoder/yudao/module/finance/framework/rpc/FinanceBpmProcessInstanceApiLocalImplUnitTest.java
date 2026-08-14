package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * FINAL-6C6F-02 补充：fail-closed、异常后 SecurityContext 恢复。
 */
class FinanceBpmProcessInstanceApiLocalImplUnitTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledIdentityFailsClosed() {
        BpmProcessInstanceApi delegate = mock(BpmProcessInstanceApi.class);
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(false);
        props.setSecret("prod-injected-rpc-service-identity-key-9f3a");
        FinanceBpmProcessInstanceApiLocalImpl local =
                new FinanceBpmProcessInstanceApiLocalImpl(delegate, props);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> local.createProcessInstanceByBusiness(1L, new BpmProcessInstanceCreateReqDTO()));
        assertTrue(ex.getMessage().contains("enabled=false") || ex.getMessage().contains("关闭"));
        verify(delegate, never()).createProcessInstanceByBusiness(anyLong(), any());
    }

    @Test
    void weakSecretFailsClosed() {
        BpmProcessInstanceApi delegate = mock(BpmProcessInstanceApi.class);
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret("change-me");
        FinanceBpmProcessInstanceApiLocalImpl local =
                new FinanceBpmProcessInstanceApiLocalImpl(delegate, props);

        assertThrows(IllegalStateException.class,
                () -> local.createProcessInstanceByBusiness(1L, new BpmProcessInstanceCreateReqDTO()));
        verify(delegate, never()).createProcessInstanceByBusiness(anyLong(), any());
    }

    @Test
    void nullSecretFailsClosed() {
        BpmProcessInstanceApi delegate = mock(BpmProcessInstanceApi.class);
        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret(null);
        FinanceBpmProcessInstanceApiLocalImpl local =
                new FinanceBpmProcessInstanceApiLocalImpl(delegate, props);

        assertThrows(IllegalStateException.class,
                () -> local.createProcessInstanceByBusiness(1L, new BpmProcessInstanceCreateReqDTO()));
        verify(delegate, never()).createProcessInstanceByBusiness(anyLong(), any());
    }

    @Test
    void securityContextRestoredAfterDelegateException() {
        Authentication original = new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(original);

        BpmProcessInstanceApi delegate = mock(BpmProcessInstanceApi.class);
        when(delegate.createProcessInstanceByBusiness(anyLong(), any()))
                .thenThrow(new RuntimeException("delegate-boom"));

        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret("prod-injected-rpc-service-identity-key-9f3a");
        FinanceBpmProcessInstanceApiLocalImpl local =
                new FinanceBpmProcessInstanceApiLocalImpl(delegate, props);

        assertThrows(RuntimeException.class,
                () -> local.createProcessInstanceByBusiness(1L, new BpmProcessInstanceCreateReqDTO()));

        Authentication after = SecurityContextHolder.getContext().getAuthentication();
        assertSame(original, after, "SecurityContext must restore original auth after exception");
        assertTrue(after.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER::equals));
    }

    @Test
    void securityContextRestoredAfterSuccess() {
        Authentication original = new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(original);

        AtomicReference<Boolean> sawDuring = new AtomicReference<>(false);
        BpmProcessInstanceApi delegate = mock(BpmProcessInstanceApi.class);
        when(delegate.createProcessInstanceByBusiness(anyLong(), any())).thenAnswer(inv -> {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            sawDuring.set(a.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER::equals));
            return CommonResult.success("ok");
        });

        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret("prod-injected-rpc-service-identity-key-9f3a");
        FinanceBpmProcessInstanceApiLocalImpl local =
                new FinanceBpmProcessInstanceApiLocalImpl(delegate, props);

        assertEquals("ok", local.createProcessInstanceByBusiness(1L, new BpmProcessInstanceCreateReqDTO()).getData());
        assertTrue(Boolean.TRUE.equals(sawDuring.get()));
        assertSame(original, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void plainCreateDoesNotElevate() {
        BpmProcessInstanceApi delegate = mock(BpmProcessInstanceApi.class);
        when(delegate.createProcessInstance(anyLong(), any())).thenAnswer(inv -> {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            boolean elevated = a != null && a.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER::equals);
            assertFalse(elevated, "createProcessInstance must not elevate");
            return CommonResult.success("pi");
        });

        RpcServiceIdentityProperties props = new RpcServiceIdentityProperties();
        props.setEnabled(true);
        props.setSecret("prod-injected-rpc-service-identity-key-9f3a");
        FinanceBpmProcessInstanceApiLocalImpl local =
                new FinanceBpmProcessInstanceApiLocalImpl(delegate, props);

        local.createProcessInstance(1L, new BpmProcessInstanceCreateReqDTO());
        verify(delegate).createProcessInstance(anyLong(), any());
    }
}
