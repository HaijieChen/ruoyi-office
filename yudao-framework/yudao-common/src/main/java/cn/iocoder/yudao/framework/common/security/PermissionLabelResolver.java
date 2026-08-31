package cn.iocoder.yudao.framework.common.security;

/**
 * 权限标识 → 中文名。system-server 用菜单目录实现；缺失时 403 仍打印标识。
 */
public interface PermissionLabelResolver {

    /**
     * @return 权限的展示名；没有则返回 null / 空白
     */
    String resolveName(String permission);
}
