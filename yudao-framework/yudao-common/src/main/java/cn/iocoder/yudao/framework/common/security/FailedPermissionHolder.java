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
        @SuppressWarnings("unchecked")
        Set<String> codes = (Set<String>) request.getAttribute(REQUEST_ATTRIBUTE);
        if (codes == null) {
            codes = new LinkedHashSet<>();
            request.setAttribute(REQUEST_ATTRIBUTE, codes);
        }
        for (String permission : permissions) {
            if (StrUtil.isNotBlank(permission)) {
                codes.add(permission);
            }
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
