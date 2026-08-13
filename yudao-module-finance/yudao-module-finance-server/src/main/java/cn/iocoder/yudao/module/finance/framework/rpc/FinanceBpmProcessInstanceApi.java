package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * EXP-87 F3/F4：Finance 专用 BPM 流程实例 Feign 客户端（带 identity interceptor）。
 * <p>
 * - 身份拦截器挂在 {@code configuration=} 上；禁止 {@code defaultConfiguration}。
 * - {@code contextId} 与基线 {@link BpmProcessInstanceApi} 隔离。
 * - {@code primary=true} + 基线 {@code primary=false}：yudao-server 双注册时
 *   {@code BpmProcessInstanceApi} 注入点唯一解析到本客户端；CRM 单独注册基线时仍唯一。
 * - Finance 三服务构造参数使用<strong>本子类型</strong>，保证一定拿到带拦截器的客户端。
 */
@FeignClient(
        name = ApiConstants.NAME,
        contextId = "financeBpmProcessInstanceApi",
        configuration = FinanceBpmProcessInstanceFeignConfiguration.class,
        primary = true)
public interface FinanceBpmProcessInstanceApi extends BpmProcessInstanceApi {
}
