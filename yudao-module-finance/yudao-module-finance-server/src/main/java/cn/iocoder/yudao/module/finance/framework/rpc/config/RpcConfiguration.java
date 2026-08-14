package cn.iocoder.yudao.module.finance.framework.rpc.config;

import cn.iocoder.yudao.module.finance.framework.rpc.FinanceBpmProcessInstanceApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Finance → BPM Feign（OpenFeign 在 classpath 且模式为 feign 时启用）。
 * <p>
 * EXP-87 F3：仅启用 {@link FinanceBpmProcessInstanceApi}；身份拦截器挂在其
 * {@code @FeignClient(configuration=…)} 上，<strong>不</strong>使用 defaultConfiguration。
 * <p>
 * 与 {@link FinanceBpmLocalApiConfiguration} 按 {@link FinanceBpmApiMode} 确定性互斥：
 * monorepo 无 openfeign → local；微服务有 openfeign → feign；可用属性强制。
 */
@Configuration(value = "financeRpcConfiguration", proxyBeanMethods = false)
@ConditionalOnClass(FeignClient.class)
@Conditional(FinanceBpmFeignApiCondition.class)
@EnableFeignClients(clients = {FinanceBpmProcessInstanceApi.class})
public class RpcConfiguration {
}
