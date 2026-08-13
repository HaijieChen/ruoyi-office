package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.framework.security.config.RpcServiceIdentityProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * EXP-87 F3：仅挂到 {@link FinanceBpmProcessInstanceApi} 的 {@code @FeignClient(configuration=)}。
 * <p>
 * <strong>故意不加</strong> {@code @Configuration}，避免被组件扫描进父 ApplicationContext
 * 从而污染其他 Feign 命名上下文。
 */
public class FinanceBpmProcessInstanceFeignConfiguration {

    @Bean
    public RequestInterceptor financeBpmProcessInstanceIdentityRequestInterceptor(
            RpcServiceIdentityProperties properties) {
        return new FinanceRpcServiceIdentityRequestInterceptor(properties);
    }
}
