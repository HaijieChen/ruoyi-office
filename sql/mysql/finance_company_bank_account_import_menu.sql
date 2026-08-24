-- 账户信息管理导入权限（幂等）
SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '账户信息导入', 'finance:company-bank-account:import', 3, 20, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM `system_menu` parent
WHERE parent.`deleted` = b'0' AND parent.`component` = 'finance/company-bank-account/index'
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu` m
      WHERE m.`deleted` = b'0' AND m.`permission` = 'finance:company-bank-account:import'
  );

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.id, m.id, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM system_role r
CROSS JOIN system_menu m
WHERE r.deleted=b'0' AND r.tenant_id=1 AND r.code IN ('finance_admin', 'business_staff')
  AND m.deleted=b'0' AND m.permission='finance:company-bank-account:import'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=1 AND rm.deleted=b'0'
  );
