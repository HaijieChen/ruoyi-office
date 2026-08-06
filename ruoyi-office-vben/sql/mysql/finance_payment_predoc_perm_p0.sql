-- PAY-P0-3：付款前置引用权限（query only；完整付款菜单见 PR1 P1-4）
-- 幂等：permission 不存在才插入；角色授权不存在才插入

SET NAMES utf8mb4;

-- 挂到财务管理目录下的按钮权限（无独立列表页；parent=finance 一级）
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '付款前置查询', 'finance:payment-application:query', 3, 90,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       '', '', NULL, NULL,
       0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:payment-application:query'
);

-- business_staff + finance_admin
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`tenant_id` = 1
  AND r.`code` IN ('business_staff', 'finance_admin')
  AND m.`deleted` = b'0' AND m.`permission` = 'finance:payment-application:query'
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
