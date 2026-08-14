package cn.iocoder.yudao.module.finance.framework.rpc.config;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Finance → BPM Feign。
 * <p>
 * EXP-87 F3：仅启用 {@link FinanceBpmProcessInstanceApi}；身份拦截器挂在其
 * {@code @FeignClient(configuration=…)} 上，<strong>不</strong>使用 defaultConfiguration。
 */
@Configuration(value = "financeRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {FinanceBpmProcessInstanceApi.class})
@EnableConfigurationProperties(RpcServiceIdentityProperties.class)
public class RpcConfiguration {
}
