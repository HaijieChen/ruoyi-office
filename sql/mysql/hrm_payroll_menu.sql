-- 薪酬菜单。核算/最低工资仅绑 hr_admin；我的工资条绑 common（不含 batch 权限）。
-- 不修改 hrm_menu_open.sql 的 common CROSS JOIN。
SET NAMES utf8mb4;

SET @hrm_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND (`name` = '人力' OR `path` = '/hrm')
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`)
SELECT '月度工资核算', 'hrm:payroll-batch:query', 2, 40, @hrm_menu_id, 'payroll-batch',
       'ep:money', 'hrm/payroll/batch/index', 'HrmPayrollBatch',
       0, b'1', b'1', b'1'
WHERE @hrm_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'hrm/payroll/batch/index'
  );

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`)
SELECT '最低工资', 'hrm:min-wage:query', 2, 41, @hrm_menu_id, 'min-wage',
       'ep:coin', 'hrm/payroll/min-wage/index', 'HrmMinWage',
       0, b'1', b'1', b'1'
WHERE @hrm_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'hrm/payroll/min-wage/index'
  );

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`)
SELECT '我的工资条', 'hrm:payroll-payslip:query', 2, 42, @hrm_menu_id, 'payslip',
       'ep:ticket', 'hrm/payroll/payslip/index', 'HrmPayrollPayslip',
       0, b'1', b'1', b'1'
WHERE @hrm_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'hrm/payroll/payslip/index'
  );

SET @batch_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/payroll/batch/index');
SET @min_wage_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/payroll/min-wage/index');
SET @payslip_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/payroll/payslip/index');

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`)
SELECT p.`name`, p.`permission`, 3, p.`sort`, p.`parent_id`, '', '', NULL, NULL,
       0, b'1', b'1', b'1'
FROM (
    SELECT '工资核算查询' AS `name`, 'hrm:payroll-batch:query' AS `permission`, 1 AS `sort`, @batch_menu_id AS `parent_id`
    UNION ALL SELECT '工资核算创建', 'hrm:payroll-batch:create', 2, @batch_menu_id
    UNION ALL SELECT '工资核算更新', 'hrm:payroll-batch:update', 3, @batch_menu_id
    UNION ALL SELECT '工资核算下发', 'hrm:payroll-batch:publish', 4, @batch_menu_id
    UNION ALL SELECT '工资核算撤回', 'hrm:payroll-batch:withdraw', 5, @batch_menu_id
    UNION ALL SELECT '工资核算导出', 'hrm:payroll-batch:export', 6, @batch_menu_id
    UNION ALL SELECT '最低工资查询', 'hrm:min-wage:query', 1, @min_wage_menu_id
    UNION ALL SELECT '最低工资更新', 'hrm:min-wage:update', 2, @min_wage_menu_id
) p
WHERE p.`parent_id` IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu` m
      WHERE m.`deleted` = b'0' AND m.`parent_id` = p.`parent_id` AND m.`permission` = p.`permission`
  );

-- common 只拿工资条页面，不拿核算/最低工资
INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, @payslip_menu_id, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
WHERE r.`deleted` = b'0' AND r.`status` = 0 AND r.`code` = 'common' AND r.`tenant_id` = 1
  AND @payslip_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = @payslip_menu_id AND rm.`deleted` = b'0' AND rm.`tenant_id` = 1
  );
