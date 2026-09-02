-- 合同 / 开票 / 商务单：导出、删除、编辑拆成独立按钮权限（幂等）
-- 编辑沿用已有 :update；商务单删除沿用已有 :delete
SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '合同签约导出' AS name, 'finance:contract-application:export' AS permission, 17 AS sort,
           'finance/contract-application/index' AS parent_component
    UNION ALL
    SELECT '合同签约删除', 'finance:contract-application:delete', 18,
           'finance/contract-application/index'
    UNION ALL
    SELECT '开票申请导出', 'finance:invoice-application:export', 21,
           'finance/invoice-application/index'
    UNION ALL
    SELECT '开票申请删除', 'finance:invoice-application:delete', 22,
           'finance/invoice-application/index'
    UNION ALL
    SELECT '商务单导出', 'finance:business-order:export', 6,
           'finance/business-order/index'
) btn
JOIN `system_menu` parent
  ON parent.`deleted` = b'0' AND parent.`component` = btn.parent_component
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- 财务管理员：合同导出/删除（已有 update）；开票导出/删除（已有 update）；商务单仅导出
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` IN (
        'finance:contract-application:export',
        'finance:contract-application:delete',
        'finance:invoice-application:export',
        'finance:invoice-application:delete',
        'finance:business-order:export'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
        AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- 商务人员：合同仅导出；开票导出/删除/更新（原先挂在 import 上能看到删改）；商务单导出
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'business_staff' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` IN (
        'finance:contract-application:export',
        'finance:invoice-application:export',
        'finance:invoice-application:delete',
        'finance:invoice-application:update',
        'finance:business-order:export'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
        AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
