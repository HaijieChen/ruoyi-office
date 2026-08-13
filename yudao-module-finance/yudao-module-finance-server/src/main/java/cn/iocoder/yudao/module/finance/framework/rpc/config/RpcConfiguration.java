package cn.iocoder.yudao.module.finance.framework.rpc.config;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceFeignConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Finance → BPM Feign。
 * <p>
 * EXP-87 F2：身份拦截器仅经 {@link FinanceBpmProcessInstanceFeignConfiguration}
 * 进入 BpmProcessInstanceApi 的 Feign 子上下文；父容器<strong>不</strong>注册全局 RequestInterceptor。
 */
@Configuration(value = "financeRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(
        clients = {BpmProcessInstanceApi.class},
        defaultConfiguration = FinanceBpmProcessInstanceFeignConfiguration.class)
@EnableConfigurationProperties(RpcServiceIdentityProperties.class)
public class RpcConfiguration {
}
