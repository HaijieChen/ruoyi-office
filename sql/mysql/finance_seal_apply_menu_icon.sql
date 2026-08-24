-- 用法务二级菜单补 icon，与「合同签约」对齐（幂等）
SET NAMES utf8mb4;

UPDATE `system_menu`
SET `icon` = 'ep:stamp',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `path` = '/finance/seal-apply'
  AND (`icon` IS NULL OR `icon` = '');
