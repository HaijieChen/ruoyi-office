package cn.iocoder.yudao.module.finance.framework.rpc.config;

import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Finance BPM API 装配模式：Local 与 Feign <strong>确定性互斥</strong>。
 * <p>
 * 配置项 {@code yudao.rpc.finance-bpm.api-mode}：
 * <ul>
 *   <li>{@code auto}（默认）— classpath 无 {@code FeignClient} → local；有 → feign</li>
 *   <li>{@code local} — 强制本地适配器（测试/显式 monorepo）</li>
 *   <li>{@code feign} — 强制 Feign 客户端</li>
 * </ul>
 * 不依赖 {@code @ConditionalOnMissingBean} 注册顺序。
 */
public final class FinanceBpmApiMode {

    public static final String PROPERTY = "yudao.rpc.finance-bpm.api-mode";
    public static final String AUTO = "auto";
    public static final String LOCAL = "local";
    public static final String FEIGN = "feign";

    private static final String FEIGN_CLIENT_CLASS = "org.springframework.cloud.openfeign.FeignClient";

    private FinanceBpmApiMode() {
    }

    public static String resolve(Environment environment, ClassLoader classLoader) {
        String raw = environment != null ? environment.getProperty(PROPERTY) : null;
        String mode = StringUtils.hasText(raw) ? raw.trim().toLowerCase() : AUTO;
        if (LOCAL.equals(mode) || FEIGN.equals(mode)) {
            return mode;
        }
        // auto
        ClassLoader cl = classLoader != null ? classLoader : FinanceBpmApiMode.class.getClassLoader();
        return ClassUtils.isPresent(FEIGN_CLIENT_CLASS, cl) ? FEIGN : LOCAL;
    }

    public static boolean useLocal(Environment environment, ClassLoader classLoader) {
        return LOCAL.equals(resolve(environment, classLoader));
    }

    public static boolean useFeign(Environment environment, ClassLoader classLoader) {
        return FEIGN.equals(resolve(environment, classLoader));
    }
}
