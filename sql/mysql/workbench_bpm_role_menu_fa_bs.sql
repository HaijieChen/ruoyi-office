-- =============================================
-- FA / BS：工作流程·审批中心完整业务权限（幂等）
-- 解决：侧栏能进「工作流程」但页面接口 403「没有该操作权限」
--
-- 原因：
-- 1) 角色只有目录/部分页面，缺 bpm:task:query 等按钮权限
-- 2) 直写 system_role_menu 不会 @CacheEvict；PermissionService 用 Redis 缓存
--    menuId→roleIds / permission→menuIds，需走 assign-role-menu 或等缓存失效
-- =============================================
SET NAMES utf8mb4;
SET @tenant_id = 1;

-- 审批中心业务包（不含流程模型/表单管理等配置菜单）
-- 1185 工作流程 / 1200 审批中心
-- 1201 我的流程 + 1202/1219/1220 按钮
-- 1207 待办 + 1221/1222
-- 1208 已办
-- 2713 抄送（permission 在菜单本身）
-- 2720 发起流程

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', @tenant_id
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0'
  AND r.`tenant_id` = @tenant_id
  AND r.`code` IN ('finance_admin', 'business_staff')
  AND m.`deleted` = b'0'
  AND (
        (m.`parent_id` = 0 AND m.`path` = '/bpm')
     OR m.`id` IN (1200, 1201, 1202, 1207, 1208, 1219, 1220, 1221, 1222, 2713, 2720)
     OR m.`permission` IN (
            'bpm:process-instance:query',
            'bpm:process-instance:create',
            'bpm:process-instance:cancel',
            'bpm:process-instance-cc:query',
            'bpm:task:query',
            'bpm:task:update'
        )
  )
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` rm
    WHERE rm.`deleted` = b'0'
      AND rm.`role_id` = r.`id`
      AND rm.`menu_id` = m.`id`
      AND rm.`tenant_id` = @tenant_id
  );

-- 运维注意：执行后请用「角色管理 → 保存菜单权限」点一次保存，或调用
-- POST /admin-api/system/permission/assign-role-menu
-- 以触发 Redis 权限缓存清理；否则最多约 1 分钟内 hasPermission 仍可能 403。
