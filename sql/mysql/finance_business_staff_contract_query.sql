-- 商务单新增会拉可选合同，商务人员需要合同查询权限
SET NAMES utf8mb4;

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'business_staff' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND m.`permission` = 'finance:contract-application:query'
  AND NOT EXISTS (
        SELECT 1 FROM `system_role_menu` rm
        WHERE rm.`deleted` = b'0' AND rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
    );
