-- =============================================
-- WB-T1：可配首页 /home 菜单 + FA/BS 角色赋权（幂等）
-- 对齐 defaultHomePath=/home，避免登录 SPA 404
-- 历史踩坑：system_role_menu.tenant_id 必须为业务租户（oa-test=1）
-- =============================================

SET NAMES utf8mb4;

-- 业务租户（oa-test）
SET @tenant_id = 1;

-- ---------- 1. 父目录 /dashboard ----------
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '工作台', '', 1, -1, 0, '/dashboard', 'lucide:layout-dashboard', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` = '/dashboard'
);

SET @dashboard_parent_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` = '/dashboard'
    ORDER BY `id` ASC LIMIT 1
);

-- ---------- 2. 可配首页 /home（用户可见名：工作台）----------
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '工作台', '', 2, 0, @dashboard_parent_id, '/home', 'lucide:home',
       'dashboard/home/index', 'DashboardHome',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @dashboard_parent_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'dashboard/home/index'
);

-- 已存在 home 菜单时：保证启用且 path/component 正确（不强制改 name，避免冲运营文案）
UPDATE `system_menu`
SET `path` = '/home',
    `component` = 'dashboard/home/index',
    `component_name` = 'DashboardHome',
    `status` = 0,
    `visible` = b'1',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `component` = 'dashboard/home/index';

-- ---------- 3. FA / BS 赋权：/dashboard 父 + /home（tenant_id=@tenant_id）----------
-- 3a finance_admin
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', @tenant_id
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = @tenant_id
  AND m.`deleted` = b'0'
  AND (
        (m.`parent_id` = 0 AND m.`path` = '/dashboard')
     OR m.`component` = 'dashboard/home/index'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
    WHERE rm.`deleted` = b'0'
      AND rm.`role_id` = r.`id`
      AND rm.`menu_id` = m.`id`
      AND rm.`tenant_id` = @tenant_id
  );

-- 3b business_staff
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', @tenant_id
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'business_staff' AND r.`tenant_id` = @tenant_id
  AND m.`deleted` = b'0'
  AND (
        (m.`parent_id` = 0 AND m.`path` = '/dashboard')
     OR m.`component` = 'dashboard/home/index'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
    WHERE rm.`deleted` = b'0'
      AND rm.`role_id` = r.`id`
      AND rm.`menu_id` = m.`id`
      AND rm.`tenant_id` = @tenant_id
  );

-- 3c 超级管理员（code=super_admin 常见；有则补，无则跳过）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', @tenant_id
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` IN ('super_admin', 'admin') AND r.`tenant_id` = @tenant_id
  AND m.`deleted` = b'0'
  AND (
        (m.`parent_id` = 0 AND m.`path` = '/dashboard')
     OR m.`component` = 'dashboard/home/index'
  )
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
    WHERE rm.`deleted` = b'0'
      AND rm.`role_id` = r.`id`
      AND rm.`menu_id` = m.`id`
      AND rm.`tenant_id` = @tenant_id
  );

-- ---------- 4. 修复误写 tenant_id=0 的 home/dashboard 赋权（历史兼容）----------
UPDATE `system_role_menu` rm
INNER JOIN `system_menu` m ON m.`id` = rm.`menu_id` AND m.`deleted` = b'0'
SET rm.`tenant_id` = @tenant_id,
    rm.`updater` = 'admin',
    rm.`update_time` = NOW()
WHERE rm.`deleted` = b'0'
  AND rm.`tenant_id` = 0
  AND (
        (m.`parent_id` = 0 AND m.`path` = '/dashboard')
     OR m.`component` = 'dashboard/home/index'
     OR m.`component` = 'dashboard/workspace/index'
  );

-- 验收提示：
-- 1) 执行后让 FA/BS 重新登录
-- 2) get-permission-info.menus 须含 path=/home（或 component=dashboard/home/index）
