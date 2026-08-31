package cn.iocoder.yudao.framework.common.security;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 当前请求上 {@code hasPermission}/{@code hasAnyPermissions} 失败过的权限标识。
 * 存在 request attribute，避免线程池 ThreadLocal 串号。
 */
public final class FailedPermissionHolder {

    static final String REQUEST_ATTRIBUTE = "FAILED_PERMISSION_IDENTIFIERS";

    private FailedPermissionHolder() {
    }

    public static void record(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return;
        }
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null) {
            return;
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (StrUtil.isNotBlank(permission)) {
                codes.add(permission);
            }
        }
        // 覆盖而非累积：只保留最近一次失败的权限门，避免复合 OR / 体内探测污染后续 403
        request.setAttribute(REQUEST_ATTRIBUTE, codes);
    }

    /** 权限检查通过时清空，避免 leftover 标到后续 AccessDeniedException。 */
    public static void clear() {
        HttpServletRequest request = ServletUtils.getRequest();
        if (request != null) {
            request.removeAttribute(REQUEST_ATTRIBUTE);
        }
    }

    /**
     * 读出并清空。403 处理器用完即清，避免后续请求误用。
     */
    public static List<String> consume() {
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null) {
            return List.of();
        }
        Object raw = request.getAttribute(REQUEST_ATTRIBUTE);
        request.removeAttribute(REQUEST_ATTRIBUTE);
        if (!(raw instanceof Set<?> codes) || codes.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(codes.size());
        for (Object code : codes) {
            if (code instanceof String s && StrUtil.isNotBlank(s)) {
                result.add(s);
            }
        }
        return result;
    }
}
