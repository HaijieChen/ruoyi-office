-- 开票申请菜单/权限 + 角色授权（幂等）
-- 组件：finance/invoice-application/index
-- processKey 发布清单见 docs/superpowers/specs/2026-07-29-finance-invoice-bpm-delegate.md

SET NAMES utf8mb4;

-- 菜单：开票申请（挂财务管理目录）
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '开票申请', '', 2, 35,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       'invoice-application', 'ep:document', 'finance/invoice-application/index', 'FinanceInvoiceApplication',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/invoice-application/index'
);

-- 按钮权限
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '开票申请查询' AS name, 'finance:invoice-application:query' AS permission, 10 AS sort
    UNION ALL SELECT '开票申请提交', 'finance:invoice-application:create', 11
    UNION ALL SELECT '开票申请重提', 'finance:invoice-application:resubmit', 12
    UNION ALL SELECT '开票申请办票', 'finance:invoice-application:issue', 13
    UNION ALL SELECT '开票申请更新', 'finance:invoice-application:update', 14
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/invoice-application/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- business_staff：发起/查询/重提
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'business_staff' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/invoice-application/index'
     OR m.`permission` IN (
            'finance:invoice-application:query',
            'finance:invoice-application:create',
            'finance:invoice-application:resubmit'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- finance_admin：查询 + 办票 + 内部落账权限
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/invoice-application/index'
     OR m.`permission` IN (
            'finance:invoice-application:query',
            'finance:invoice-application:issue',
            'finance:invoice-application:update'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
