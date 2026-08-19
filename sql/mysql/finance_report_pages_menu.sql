-- 毛利 / 银行余额 / 部门利润 菜单（幂等）
SET NAMES utf8mb4;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.name, '', 2, t.sort,
       (SELECT `id` FROM `system_menu` WHERE `deleted` = b'0' AND `type` = 1 AND `parent_id` = 0 AND `path` IN ('finance', '/finance') LIMIT 1),
       t.path, t.icon, t.component, t.cname,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '产品毛利表' name, 51 sort, 'report-gross-margin' path, 'ep:trend-charts' icon,
           'finance/report/gross-margin/index' component, 'FinanceGrossMarginReport' cname
    UNION ALL SELECT '银行余额表', 52, 'report-bank-balance', 'ep:wallet',
           'finance/report/bank-balance/index', 'FinanceBankBalanceReport'
    UNION ALL SELECT '部门利润表', 53, 'report-dept-profit', 'ep:data-analysis',
           'finance/report/dept-profit/index', 'FinanceDeptProfitReport'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m WHERE m.`deleted` = b'0' AND m.`component` = t.component
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, 10, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '产品毛利查询' name, 'finance:report-gross-margin:query' permission, 'finance/report/gross-margin/index' component
    UNION ALL SELECT '银行余额查询', 'finance:report-bank-balance:query', 'finance/report/bank-balance/index'
    UNION ALL SELECT '银行期初更新', 'finance:bank-opening:update', 'finance/report/bank-balance/index'
    UNION ALL SELECT '部门利润查询', 'finance:report-dept-profit:query', 'finance/report/dept-profit/index'
    UNION ALL SELECT '部门分摊导入', 'finance:dept-allocation:import', 'finance/report/dept-profit/index'
) btn
JOIN `system_menu` parent ON parent.`deleted` = b'0' AND parent.`component` = btn.component
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`component` IN (
            'finance/report/gross-margin/index',
            'finance/report/bank-balance/index',
            'finance/report/dept-profit/index')
     OR m.`permission` IN (
            'finance:report-gross-margin:query',
            'finance:report-bank-balance:query',
            'finance:bank-opening:update',
            'finance:report-dept-profit:query',
            'finance:dept-allocation:import')
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
