package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * EXP-87 F2：仅绑定 {@link cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi} 的 Feign 子上下文配置。
 * <p>
 * <strong>故意不加</strong> {@code @Configuration}：若被组件扫描进父 ApplicationContext，
 * RequestInterceptor 会污染 System/Infra/其他 Feign 客户端。
 * 仅通过 {@code @EnableFeignClients(defaultConfiguration = ...)} 引入。
 */
public class FinanceBpmProcessInstanceFeignConfiguration {

    @Bean
    public RequestInterceptor financeBpmProcessInstanceIdentityRequestInterceptor(
            RpcServiceIdentityProperties properties) {
        return new FinanceRpcServiceIdentityRequestInterceptor(properties);
    }
}
