-- 薪资/税金付款隐藏详情页（幂等）：列表「详情」与 BPM 自定义表单共用组件，避免 404
SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.name, '', 2, t.sort, p.id, t.path, '', t.component, t.cname,
       0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '薪资付款详情' name, 4 sort, 'salary-payment/detail' path,
           'finance/salary-payment/detail/index' component, 'FinanceSalaryPaymentDetail' cname
    UNION ALL SELECT '税金付款详情', 5, 'tax-payment/detail',
           'finance/tax-payment/detail/index', 'FinanceTaxPaymentDetail'
) t
JOIN `system_menu` p
  ON p.`deleted` = b'0'
 AND p.`component` = CASE t.component
        WHEN 'finance/salary-payment/detail/index' THEN 'finance/salary-payment/index'
        WHEN 'finance/tax-payment/detail/index' THEN 'finance/tax-payment/index'
     END
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m WHERE m.`deleted` = b'0' AND m.`component` = t.component
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`tenant_id` = 1
  AND r.`code` IN ('finance_admin', 'business_staff', 'payment_cashier')
  AND m.`deleted` = b'0'
  AND m.`component` IN (
        'finance/salary-payment/detail/index',
        'finance/tax-payment/detail/index')
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
