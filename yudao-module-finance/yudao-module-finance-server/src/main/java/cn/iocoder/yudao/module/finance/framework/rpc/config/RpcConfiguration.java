package cn.iocoder.yudao.module.finance.framework.rpc.config;

import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Configuration;

/**
 * Finance → BPM Feign（仅微服务 / openfeign 在 classpath 时启用）。
 * <p>
 * EXP-87 F3：仅启用 {@link FinanceBpmProcessInstanceApi}；身份拦截器挂在其
 * {@code @FeignClient(configuration=…)} 上，<strong>不</strong>使用 defaultConfiguration。
 * <p>
 * 单体 {@code yudao-server} 排除 openfeign：本配置因 {@link ConditionalOnClass} 不加载，
 * 改由 {@link FinanceBpmLocalApiConfiguration} 提供本地 Bean。
 */
@Configuration(value = "financeRpcConfiguration", proxyBeanMethods = false)
@ConditionalOnClass(FeignClient.class)
@EnableFeignClients(clients = {FinanceBpmProcessInstanceApi.class})
public class RpcConfiguration {
}
