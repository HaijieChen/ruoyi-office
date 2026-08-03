-- finance_admin：组织架构（人力 → 组织架构 → 组织管理/架构图）+ system:dept CRUD
-- 幂等追加，不 DELETE 已有角色菜单（与 customer-company 授权脚本同风格）
-- 用途：财务维护主体公司（组织类型=公司）等组织节点

SET NAMES utf8mb4;

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` = 'finance_admin' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        -- 人力目录 + 组织架构子树（前端「组织架构」入口）
        m.`id` IN (5130, 5131, 5132, 5133)
        -- 部门/组织 API 按钮权限
     OR m.`permission` IN (
            'system:dept:query',
            'system:dept:create',
            'system:dept:update',
            'system:dept:delete'
        )
        -- 系统管理下「部门管理」页（与 组织管理 同 component，便于从系统侧进入）
     OR m.`id` = 103
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
