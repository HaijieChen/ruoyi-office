package cn.iocoder.yudao.module.finance.framework.rpc.config;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApiLocalImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 单体 yudao-server：在无 Feign 客户端 Bean 时注册本地 {@link FinanceBpmProcessInstanceApi}。
 * <p>
 * OpenFeign 微服务模式下 {@link RpcConfiguration} 会注册 Feign 客户端，本 Bean 因
 * {@link ConditionalOnMissingBean} 跳过。
 */
@Configuration(value = "financeBpmLocalApiConfiguration", proxyBeanMethods = false)
@EnableConfigurationProperties(RpcServiceIdentityProperties.class)
public class FinanceBpmLocalApiConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(FinanceBpmProcessInstanceApi.class)
    public FinanceBpmProcessInstanceApi financeBpmProcessInstanceApiLocal(
            ObjectProvider<BpmProcessInstanceApi> bpmProcessInstanceApis,
            RpcServiceIdentityProperties identityProperties) {
        BpmProcessInstanceApi delegate = bpmProcessInstanceApis.orderedStream()
                .filter(api -> !(api instanceof FinanceBpmProcessInstanceApi))
                .findFirst()
                .orElseGet(bpmProcessInstanceApis::getObject);
        return new FinanceBpmProcessInstanceApiLocalImpl(delegate, identityProperties);
    }
}
