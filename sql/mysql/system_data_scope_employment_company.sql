-- 角色数据范围：任职公司（value=6）
SET NAMES utf8mb4;

INSERT INTO `system_dict_data` (
  `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
  `creator`, `create_time`, `updater`, `update_time`, `deleted`
)
SELECT 6, '任职公司数据权限', '6', 'system_data_scope', 0, '', '',
       '用户全部任职公司及其下属部门', 'admin', NOW(), 'admin', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `system_dict_data`
  WHERE `dict_type` = 'system_data_scope' AND `value` = '6' AND `deleted` = b'0'
);
