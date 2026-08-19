-- 费用报销菜单（幂等）。create 无权限点，登录即可。
SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.name, '', t.type, t.sort,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       t.path, t.icon, t.component, t.cname,
       0, t.visible, b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '费用报销' name, 2 type, 40 sort, 'expense-reimbursement' path, 'ep:ticket' icon,
           'finance/expense-reimbursement/index' component, 'FinanceExpenseReimbursement' cname, b'1' visible
    UNION ALL SELECT '发起费用报销', 2, 41, 'expense-reimbursement/create', '',
           'finance/expense-reimbursement/create', 'FinanceExpenseReimbursementCreate', b'0'
    UNION ALL SELECT '费用报销详情', 2, 42, 'expense-reimbursement/detail', '',
           'finance/expense-reimbursement/detail', 'FinanceExpenseReimbursementDetail', b'0'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m WHERE m.`deleted` = b'0' AND m.`component` = t.component
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '费用报销查询' name, 'finance:expense:query' permission, 10 sort
    UNION ALL SELECT '费用报销出纳登记', 'finance:expense:record-pay', 11
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/expense-reimbursement/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        (r.`code` IN ('finance_admin', 'hr_admin') AND (
            m.`component` IN (
                'finance/expense-reimbursement/index',
                'finance/expense-reimbursement/create',
                'finance/expense-reimbursement/detail')
         OR m.`permission` IN ('finance:expense:query', 'finance:expense:record-pay')))
     OR (r.`code` = 'business_staff' AND m.`component` IN (
            'finance/expense-reimbursement/index',
            'finance/expense-reimbursement/create',
            'finance/expense-reimbursement/detail'))
     OR (r.`code` = 'finance_admin' AND m.`permission` = 'finance:expense:record-pay')
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
