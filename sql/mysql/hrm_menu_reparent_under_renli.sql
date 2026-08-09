-- 将已开放的人力实现菜单挂回现有一级「人力」下，并软删平行根「人力资源管理」。
-- 幂等；可与 hrm_menu_open.sql 配合使用（后者已改为优先复用「人力」）。

SET NAMES utf8mb4;
START TRANSACTION;

SET @renli_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `name` = '人力'
    ORDER BY `id` LIMIT 1
);

SET @archive_dir_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @renli_id AND `name` = '人事档案'
    ORDER BY `id` LIMIT 1
);

SET @pm_dir_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @renli_id AND `name` = '人事管理'
    ORDER BY `id` LIMIT 1
);

-- 员工档案 → 人力/人事档案
UPDATE `system_menu`
SET `parent_id` = COALESCE(@archive_dir_id, @renli_id), `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'hrm/employee/list/index';

-- 入职/转正/离职/调动 → 人力/人事管理
UPDATE `system_menu`
SET `parent_id` = COALESCE(@pm_dir_id, @renli_id), `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` IN (
    'hrm/employee-relation/entry/list/index',
    'hrm/employee-relation/regular/list/index',
    'hrm/employee-relation/resignation/list/index',
    'hrm/employee-relation/transfer/list/index'
);

-- 软删平行一级根
UPDATE `system_menu`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND `parent_id` = 0 AND `name` = '人力资源管理'
  AND (`id` <> @renli_id OR @renli_id IS NULL);

-- common 授权补齐中间目录
INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`tenant_id` = 1 AND r.`code` = 'common' AND r.`status` = 0
  AND m.`deleted` = b'0' AND m.`id` IN (@renli_id, @archive_dir_id, @pm_dir_id)
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
        AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

COMMIT;
