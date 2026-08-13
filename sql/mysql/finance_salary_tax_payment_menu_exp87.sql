-- EXP-87 P2：薪资/税金付款菜单权限（独立入口；审批链复用 payment_* 角色）

SET NAMES utf8mb4;

-- 薪资付款申请
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '薪资付款申请', '', 2, 2,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       'salary-payment', 'ep:money', 'finance/salary-payment/index', 'FinanceSalaryPayment',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/salary-payment/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '薪资付款查询' AS name, 'finance:salary-payment:query' AS permission, 10 AS sort
    UNION ALL SELECT '薪资付款创建', 'finance:salary-payment:create', 11
    UNION ALL SELECT '薪资付款更新', 'finance:salary-payment:update', 12
    UNION ALL SELECT '薪资付款重提', 'finance:salary-payment:resubmit', 13
    UNION ALL SELECT '薪资付款出纳登记', 'finance:salary-payment:record-pay', 14
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/salary-payment/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- 税金付款申请
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '税金付款申请', '', 2, 3,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       'tax-payment', 'ep:document', 'finance/tax-payment/index', 'FinanceTaxPayment',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/tax-payment/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '税金付款查询' AS name, 'finance:tax-payment:query' AS permission, 10 AS sort
    UNION ALL SELECT '税金付款创建', 'finance:tax-payment:create', 11
    UNION ALL SELECT '税金付款更新', 'finance:tax-payment:update', 12
    UNION ALL SELECT '税金付款重提', 'finance:tax-payment:resubmit', 13
    UNION ALL SELECT '税金付款出纳登记', 'finance:tax-payment:record-pay', 14
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/tax-payment/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- finance_admin 全量
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` IN ('finance/salary-payment/index', 'finance/tax-payment/index')
     OR m.`permission` LIKE 'finance:salary-payment:%'
     OR m.`permission` LIKE 'finance:tax-payment:%'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- payment_cashier：query + record-pay（两类）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'payment_cashier' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` IN (
        'finance:salary-payment:query', 'finance:salary-payment:record-pay',
        'finance:tax-payment:query', 'finance:tax-payment:record-pay'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- business_staff：创建/查询/重提
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'business_staff' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` IN ('finance/salary-payment/index', 'finance/tax-payment/index')
     OR m.`permission` IN (
            'finance:salary-payment:query', 'finance:salary-payment:create', 'finance:salary-payment:resubmit',
            'finance:tax-payment:query', 'finance:tax-payment:create', 'finance:tax-payment:resubmit'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
