package cn.iocoder.yudao.framework.common.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.spring.SpringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * 权限门 403 的 R1 文案：缺少权限「中文名」（identifier）；无名则 缺少权限（identifier）。
 */
public final class PermissionDeniedMessageFormatter {

    private PermissionDeniedMessageFormatter() {
    }

    /**
     * @return R1 文案；没有权限码时返回 null，调用方应保留通用「没有该操作权限」
     */
    public static String format(Collection<String> codes, Function<String, String> labelLookup) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String code : codes) {
            if (StrUtil.isBlank(code)) {
                continue;
            }
            String name = null;
            if (labelLookup != null) {
                try {
                    name = labelLookup.apply(code);
                } catch (Exception ignored) {
                    // SPI 失败仍返回可授权的 identifier（R6）
                }
            }
            if (StrUtil.isBlank(name)) {
                parts.add("缺少权限（" + code + "）");
            } else {
                parts.add("缺少权限「" + name + "」（" + code + "）");
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("、", parts);
    }

    /**
     * 从当前请求取出失败权限码并格式化；读完即清空。
     */
    public static String formatForCurrentRequest() {
        List<String> codes = FailedPermissionHolder.consume();
        if (codes.isEmpty()) {
            return null;
        }
        return format(codes, PermissionDeniedMessageFormatter::resolveLabel);
    }

    private static String resolveLabel(String code) {
        try {
            PermissionLabelResolver resolver = SpringUtils.getBean(PermissionLabelResolver.class);
            return resolver == null ? null : resolver.resolveName(code);
        } catch (Exception ignored) {
            return null;
        }
    }
}
