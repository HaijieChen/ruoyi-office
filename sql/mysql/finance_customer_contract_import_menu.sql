-- 客户公司 / 合同签约导入权限（幂等）
SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '客户公司导入' AS name, 'finance:customer-company:import' AS permission, 13 AS sort,
           'finance/customer-company/index' AS parent_component
    UNION ALL
    SELECT '合同签约导入', 'finance:contract-application:import', 16,
           'finance/contract-application/index'
) btn
JOIN `system_menu` parent
  ON parent.`deleted` = b'0' AND parent.`component` = btn.parent_component
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` IN (
        'finance:customer-company:import',
        'finance:contract-application:import'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
        AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
