-- =============================================================================
-- 财务管理一级菜单 path 修正：finance -> /finance
--
-- 背景：
--   phase1a 创建的一级菜单 path 为相对路径 `finance`，而其它一级菜单（如 /oa、/crm、/erp）
--   使用以 `/` 开头的绝对 path。后端菜单模式（accessMode=backend）下，缺少前导 `/`
--   会导致侧栏路由异常或「到款认领」等入口不易访问。
--
-- 范围：
--   仅修正 type=1 且 parent_id=0 的「财务管理」根菜单。
--   不修改 ERP 下的「财务管理」（id=2645 一类，path=finance 且 parent_id<>0）。
--
-- 幂等：可重复执行。
-- =============================================================================

SET NAMES utf8mb4;

UPDATE `system_menu`
SET `path` = '/finance',
    `updater` = IFNULL(NULLIF(`updater`, ''), '1'),
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `type` = 1
  AND `parent_id` = 0
  AND `path` = 'finance';
