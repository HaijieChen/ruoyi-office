-- =============================================
-- 工作台菜单去重：侧栏只显示可配首页 /home
-- 保留 /workspace 菜单行但 visible=0，以便动态路由仍注册并 redirect
-- =============================================
SET NAMES utf8mb4;

-- 可配首页：用户可见「工作台」
UPDATE `system_menu`
SET `name` = '工作台',
    `sort` = 0,
    `visible` = b'1',
    `status` = 0,
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `component` = 'dashboard/home/index';

-- 旧 workspace：侧栏隐藏（hideInMenu），名称避免与 /home 撞名
UPDATE `system_menu`
SET `name` = '工作台(兼容旧链)',
    `sort` = 99,
    `visible` = b'0',
    `status` = 0,
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `component` = 'dashboard/workspace/index';
