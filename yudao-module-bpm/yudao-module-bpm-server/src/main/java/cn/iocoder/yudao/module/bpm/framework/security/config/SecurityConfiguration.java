package cn.iocoder.yudao.module.bpm.framework.security.config;

import cn.iocoder.yudao.framework.common.util.rpc.RpcServiceIdentityConstants;
import cn.iocoder.yudao.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import cn.iocoder.yudao.module.bpm.framework.security.BpmBusinessStartIdentityFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Bpm 模块的 Security 配置
 */
@Configuration(proxyBeanMethods = false, value = "bpmSecurityConfiguration")
@EnableConfigurationProperties(RpcServiceIdentityProperties.class)
public class SecurityConfiguration {

    @Bean
    public BpmBusinessStartIdentityFilter bpmBusinessStartIdentityFilter(
            RpcServiceIdentityProperties properties) {
        return new BpmBusinessStartIdentityFilter(properties);
    }

    /**
     * EXP-87 G1：create-by-business 独立 SecurityFilterChain（不在任意调用方 permitAll 语义下）。
     * 仅 Finance 服务身份（HMAC 校验后 authority）可访问；Filter 在鉴权前写入 Authentication。
     */
    @Bean
    @Order(1)
    public SecurityFilterChain bpmCreateByBusinessSecurityFilterChain(
            HttpSecurity httpSecurity,
            BpmBusinessStartIdentityFilter identityFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {
        httpSecurity
                .securityMatcher(ApiConstants.PREFIX + "/process-instance/create-by-business")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(c -> c.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(c -> c.anyRequest()
                        .hasAuthority(RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER))
                .addFilterBefore(identityFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    @Bean("bpmAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // Swagger 接口文档
                registry.requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll();
                // Druid 监控
                registry.requestMatchers("/druid/**").permitAll();
                // Spring Boot Actuator 的安全配置
                registry.requestMatchers("/actuator").permitAll()
                        .requestMatchers("/actuator/**").permitAll();
                // 主链：create-by-business 由独立 SecurityFilterChain 处理；此处再显式声明，避免被 /** permitAll 吞掉
                registry.requestMatchers(HttpMethod.POST,
                                ApiConstants.PREFIX + "/process-instance/create-by-business")
                        .hasAuthority(RpcServiceIdentityConstants.AUTHORITY_FINANCE_SERVER);
                // 其它 RPC 仍 permitAll（通用 create 对薪税 hide+deny）
                registry.requestMatchers(ApiConstants.PREFIX + "/**").permitAll();
            }

        };
    }
}
