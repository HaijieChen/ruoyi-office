-- EXP-73 BPM-1：付款财务/出纳角色最小权限 + 测试环境候选人绑定（幂等）
-- 约束：
--   - 保留 payment_finance / payment_cashier，与 finance_admin 权限角色分离
--   - 禁止把全体 finance_admin 默认当审批人
--   - 测试环境：把 financeadminuser 挂到 payment_finance + payment_cashier 以便联调
--   - 生产真实名单：业务拍板后替换下方绑定（见注释占位）

SET NAMES utf8mb4;

-- ========== 0. 确保角色存在（与 finance_payment_application_menu.sql 对齐） ==========
INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.name, r.code, r.sort, 1, '', 0, 2, r.remark,
       'admin', NOW(), 'admin', NOW(), b'0', 1
FROM (
    SELECT '付款财务主管' AS name, 'payment_finance' AS code, 52 AS sort,
           'BPM finance_payment_apply taskFinance' AS remark
    UNION ALL SELECT '付款出纳', 'payment_cashier', 53,
           'BPM finance_payment_apply taskCashier'
) r
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role` sr WHERE sr.`deleted` = b'0' AND sr.`code` = r.code AND sr.`tenant_id` = 1
);

-- ========== 1. payment_finance：query + update（财务填科目）+ BPM 待办办理依赖平台权限 ==========
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'payment_finance' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` = 'finance/payment-application/index'
     OR m.`permission` IN (
            'finance:payment-application:query',
            'finance:payment-application:update'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

-- ========== 2. payment_cashier：query + record-pay（幂等补齐） ==========
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

-- ========== 3. 测试环境候选人：financeadminuser → payment_finance + payment_cashier ==========
-- 生产：删除/替换本段，按业务名单绑定，切勿把全部 finance_admin 用户默认挂入。
INSERT INTO `system_user_role`
    (`user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT u.`id`, r.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_users` u
CROSS JOIN `system_role` r
WHERE u.`deleted` = b'0' AND u.`username` = 'financeadminuser' AND u.`tenant_id` = 1
  AND r.`deleted` = b'0' AND r.`code` IN ('payment_finance', 'payment_cashier') AND r.`tenant_id` = 1
  AND NOT EXISTS (
      SELECT 1 FROM `system_user_role` ur
      WHERE ur.`user_id` = u.`id` AND ur.`role_id` = r.`id`
        AND ur.`tenant_id` = 1 AND ur.`deleted` = b'0'
  );

-- ========== 4. 生产占位（业务拍板后执行；示例） ==========
-- INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
-- SELECT u.id, r.id, 'admin', NOW(), 'admin', NOW(), b'0', 1
-- FROM system_users u
-- CROSS JOIN system_role r
-- WHERE u.username IN (/* payment_finance 真实账号 */)
--   AND r.code = 'payment_finance' AND u.tenant_id = 1 AND r.tenant_id = 1 AND u.deleted = b'0' AND r.deleted = b'0'
--   AND NOT EXISTS (...);
-- 同理 payment_cashier。

-- ========== 5. 巡检：角色至少 1 名启用用户（门禁/运维） ==========
-- SELECT r.code, COUNT(ur.user_id) AS member_cnt
-- FROM system_role r
-- LEFT JOIN system_user_role ur ON ur.role_id = r.id AND ur.deleted = b'0' AND ur.tenant_id = r.tenant_id
-- LEFT JOIN system_users u ON u.id = ur.user_id AND u.deleted = b'0' AND u.status = 0
-- WHERE r.deleted = b'0' AND r.tenant_id = 1 AND r.code IN ('payment_finance', 'payment_cashier')
-- GROUP BY r.code;
