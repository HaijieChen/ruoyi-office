package cn.iocoder.yudao.module.finance.framework.rpc.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 仅在 Feign 模式启用 {@link RpcConfiguration}（还需 {@code @ConditionalOnClass(FeignClient)}）。
 */
public class FinanceBpmFeignApiCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return FinanceBpmApiMode.useFeign(context.getEnvironment(), context.getClassLoader());
    }
}
