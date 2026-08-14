package cn.iocoder.yudao.module.finance.framework.rpc.config;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApiLocalImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * 单体 yudao-server：注册本地 {@link FinanceBpmProcessInstanceApi}。
 * <p>
 * 与 {@link RpcConfiguration} 按 {@link FinanceBpmApiMode} <strong>确定性互斥</strong>
 * （classpath / 显式 {@code yudao.rpc.finance-bpm.api-mode}），不依赖 Bean 注册顺序。
 * <p>
 * <strong>无 {@code @Primary}</strong>：父类型 {@link BpmProcessInstanceApi} 仍解析到基线
 * {@code BpmProcessInstanceApiImpl}；仅精确子类型注入走本适配器（防提权扩大）。
 */
@Configuration(value = "financeBpmLocalApiConfiguration", proxyBeanMethods = false)
@Conditional(FinanceBpmLocalApiCondition.class)
@EnableConfigurationProperties(RpcServiceIdentityProperties.class)
public class FinanceBpmLocalApiConfiguration {

    @Bean(name = "financeBpmProcessInstanceApiLocal")
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
