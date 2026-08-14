package cn.iocoder.yudao.module.finance.framework.rpc.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 仅在 Local 模式启用 {@link FinanceBpmLocalApiConfiguration}。
 */
public class FinanceBpmLocalApiCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return FinanceBpmApiMode.useLocal(context.getEnvironment(), context.getClassLoader());
    }
}
