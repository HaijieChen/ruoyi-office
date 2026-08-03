package cn.iocoder.yudao.module.system.api.permission;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.service.permission.RoleService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class RoleApiImpl implements RoleApi {

    @Resource
    private RoleService roleService;
    @Resource
    private RoleMapper roleMapper;

    @Override
    public CommonResult<Boolean> validRoleList(Collection<Long> ids) {
        roleService.validateRoleList(ids);
        return success(true);
    }

    @Override
    public CommonResult<List<Long>> getRoleIdListByCodes(Collection<String> codes) {
        List<Long> ids = new ArrayList<>();
        if (CollUtil.isEmpty(codes)) {
            return success(ids);
        }
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            RoleDO role = roleMapper.selectByCode(code.trim());
            if (role != null && role.getId() != null) {
                ids.add(role.getId());
            }
        }
        return success(ids);
    }
}
