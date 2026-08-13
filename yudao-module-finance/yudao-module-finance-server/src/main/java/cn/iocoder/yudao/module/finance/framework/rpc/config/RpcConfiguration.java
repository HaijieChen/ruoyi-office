package cn.iocoder.yudao.module.finance.framework.rpc.config;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceRpcServiceIdentityRequestInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "financeRpcConfiguration", proxyBeanMethods = false)
@EnableFeignClients(clients = {BpmProcessInstanceApi.class})
@EnableConfigurationProperties(RpcServiceIdentityProperties.class)
public class RpcConfiguration {

    /** EXP-87 G1：出站 Feign 附带 Finance 服务身份 HMAC */
    @Bean
    public FinanceRpcServiceIdentityRequestInterceptor financeRpcServiceIdentityRequestInterceptor(
            RpcServiceIdentityProperties properties) {
        return new FinanceRpcServiceIdentityRequestInterceptor(properties);
    }
}
