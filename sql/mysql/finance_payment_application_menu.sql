-- 付款申请菜单/权限 + 角色授权（PAY-P1-4，幂等）
-- 组件：finance/payment-application/index
-- processKey = finance_payment_apply

SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '付款申请', '', 2, 36,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       'payment-application', 'ep:money', 'finance/payment-application/index', 'FinancePaymentApplication',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/payment-application/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '付款申请查询' AS name, 'finance:payment-application:query' AS permission, 10 AS sort
    UNION ALL SELECT '付款申请提交', 'finance:payment-application:create', 11
    UNION ALL SELECT '付款申请重提', 'finance:payment-application:resubmit', 12
    UNION ALL SELECT '付款申请更新', 'finance:payment-application:update', 13
    UNION ALL SELECT '付款出纳登记', 'finance:payment-application:record-pay', 14
    -- PAY-R14/R16：运维重放终态（专用权限；update 不隐含 replay）
    UNION ALL SELECT '付款终态重放', 'finance:payment-application:replay-outcome', 15
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'finance/payment-application/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- BPM 角色
INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.name, r.code, r.sort, 1, '', 0, 2, r.remark,
       'admin', NOW(), 'admin', NOW(), b'0', 1
FROM (
    SELECT '付款部门负责人' AS name, 'payment_dept_head' AS code, 50 AS sort, 'BPM finance_payment_apply taskDeptHead' AS remark
    UNION ALL SELECT '付款业务负责人', 'payment_biz_head', 51, 'BPM finance_payment_apply taskBizHead'
    UNION ALL SELECT '付款财务主管', 'payment_finance', 52, 'BPM finance_payment_apply taskFinance'
    UNION ALL SELECT '付款出纳', 'payment_cashier', 53, 'BPM finance_payment_apply taskCashier'
) r
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` sr WHERE sr.`deleted` = b'0' AND sr.`code` = r.code AND sr.`tenant_id` = 1
);

-- business_staff：发起/查询/重提
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'business_staff' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/payment-application/index'
     OR m.`permission` IN (
            'finance:payment-application:query',
            'finance:payment-application:create',
            'finance:payment-application:resubmit'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- finance_admin：全量读(update) + 列表 + 专用终态重放(replay-outcome，PAY-R16 与 update 解耦)
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/payment-application/index'
     OR m.`permission` IN (
            'finance:payment-application:query',
            'finance:payment-application:update',
            'finance:payment-application:replay-outcome'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- 出纳角色：query + record-pay
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'payment_cashier' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` IN (
        'finance:payment-application:query',
        'finance:payment-application:record-pay'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
