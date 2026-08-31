-- 组织架构导出权限 system:dept:export
-- 幂等：可重复执行。
-- 1) 人力 → 组织架构 → 组织管理 下挂导出按钮
-- 2) 系统管理侧部门菜单（parent_id=103）下挂同权按钮
-- 3) 仅授权 hr_admin

SET NAMES utf8mb4;

SET @organization_page_menu_id = (
    SELECT `id`
    FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `name` = '组织管理'
      AND `component` = 'system/dept/index'
      AND `parent_id` IN (
          SELECT `id` FROM `system_menu`
          WHERE `deleted` = b'0' AND `name` = '组织架构'
      )
    ORDER BY `id`
    LIMIT 1
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '组织导出', 'system:dept:export', 3, 6, @organization_page_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @organization_page_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `parent_id` = @organization_page_menu_id
        AND `permission` = 'system:dept:export'
  );

SET @system_dept_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `id` = 103
    LIMIT 1
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '部门导出', 'system:dept:export', 3, 6, @system_dept_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @system_dept_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `parent_id` = @system_dept_menu_id
        AND `permission` = 'system:dept:export'
  );

SET @hr_admin_role_id = (
    SELECT MIN(`id`) FROM `system_role`
    WHERE `deleted` = b'0' AND `code` = 'hr_admin' AND `tenant_id` = 1
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT @hr_admin_role_id, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_menu` m
WHERE m.`deleted` = b'0'
  AND m.`permission` = 'system:dept:export'
  AND @hr_admin_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`deleted` = b'0' AND rm.`tenant_id` = 1
        AND rm.`role_id` = @hr_admin_role_id AND rm.`menu_id` = m.`id`
  );
