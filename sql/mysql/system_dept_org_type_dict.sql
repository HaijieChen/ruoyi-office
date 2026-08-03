-- 组织类型字典 system_dept_org_type（0部门 / 1公司）
-- 对应 FE DICT_TYPE.SYSTEM_DEPT_ORG_TYPE、BE OrgTypeEnum；幂等
-- 缺失时「新增组织」组织类型下拉显示「暂无数据」

SET NAMES utf8mb4;

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '部门组织类型', 'system_dept_org_type', 0, '组织架构 orgType：0部门 1公司', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` WHERE `type` = 'system_dept_org_type' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '部门', '0', 'system_dept_org_type', 0, 'default', '', '组织类型-部门', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_dept_org_type' AND `value` = '0' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '公司', '1', 'system_dept_org_type', 0, 'primary', '', '组织类型-公司', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'system_dept_org_type' AND `value` = '1' AND `deleted` = b'0'
);
