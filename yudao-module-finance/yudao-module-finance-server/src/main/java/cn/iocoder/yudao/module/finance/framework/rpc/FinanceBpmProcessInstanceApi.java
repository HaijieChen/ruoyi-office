package cn.iocoder.yudao.module.finance.framework.rpc;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * EXP-87 F3：Finance 专用 BPM 流程实例 Feign 客户端。
 * <p>
 * 身份拦截器挂在本接口的 {@code configuration=} 上（OpenFeign 命名上下文仅此客户端），
 * <strong>禁止</strong>使用 {@code @EnableFeignClients(defaultConfiguration=…)}。
 * <p>
 * {@code contextId} 与共享的 {@link BpmProcessInstanceApi} 隔离；{@code primary=true}
 * 保证 Finance 模块注入 {@link BpmProcessInstanceApi} 时命中本客户端。
 */
@FeignClient(
        name = ApiConstants.NAME,
        contextId = "financeBpmProcessInstanceApi",
        configuration = FinanceBpmProcessInstanceFeignConfiguration.class,
        primary = true)
public interface FinanceBpmProcessInstanceApi extends BpmProcessInstanceApi {
}
