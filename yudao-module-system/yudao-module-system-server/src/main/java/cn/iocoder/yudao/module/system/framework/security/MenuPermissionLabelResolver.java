package cn.iocoder.yudao.module.system.framework.security;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.security.PermissionLabelResolver;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.MenuMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用 system_menu.name 解析权限标识的中文名。
 */
@Component
public class MenuPermissionLabelResolver implements PermissionLabelResolver {

    @Resource
    private MenuMapper menuMapper;

    @Override
    public String resolveName(String permission) {
        if (StrUtil.isBlank(permission)) {
            return null;
        }
        List<MenuDO> menus = menuMapper.selectListByPermission(permission);
        if (CollUtil.isEmpty(menus)) {
            return null;
        }
        for (MenuDO menu : menus) {
            if (menu != null && StrUtil.isNotBlank(menu.getName())) {
                return menu.getName();
            }
        }
        return null;
    }
}
