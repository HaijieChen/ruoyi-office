-- 无票报销入口对业务角色可见（幂等）
SET NAMES utf8mb4;

UPDATE `system_menu`
SET `visible` = b'1',
    `name` = '无票费用报销',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `component` = 'finance/expense-reimbursement/no-invoice-create';

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`tenant_id` = 1
  AND r.`code` IN ('business_staff', 'finance_admin', 'hr_admin')
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/expense-reimbursement/no-invoice-create'
     OR m.`permission` = 'finance:expense-no-invoice:create'
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
