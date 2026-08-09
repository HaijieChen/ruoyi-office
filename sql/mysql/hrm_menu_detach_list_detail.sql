-- 列表菜单不得把详情页当作子节点，否则前端 convertServerMenuToRouteRecordStringComponent
-- 会在 parentId!=0 且有 children 时清空列表 component，点菜单直达详情/新增页。
-- 将五类详情与列表改为平级挂在目录下（幂等）。

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

UPDATE `system_menu`
SET `parent_id` = COALESCE(@archive_dir_id, @renli_id), `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` = 'hrm/employee/info/index';

UPDATE `system_menu`
SET `parent_id` = COALESCE(@pm_dir_id, @renli_id), `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND `component` IN (
    'hrm/employee-relation/entry/info/index',
    'hrm/employee-relation/regular/info/index',
    'hrm/employee-relation/resignation/info/index',
    'hrm/employee-relation/transfer/info/index'
);

COMMIT;
