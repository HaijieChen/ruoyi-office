-- 人力资源菜单/权限开放（幂等）
--
-- 覆盖当前已实现的员工档案、入职、转正、调动、离职页面；详情页保留为隐藏
-- 动态路由。普通角色（tenant_id = 1 的 common）获得这棵菜单树的完整权限。
--
-- 产品口径：子菜单挂在现有一级「人力」下，禁止再造平行根「人力资源管理」。

SET NAMES utf8mb4;
START TRANSACTION;

-- 一级菜单：优先复用现有「人力」(parent_id=0, path=/hrm 或 name=人力)
SET @hrm_menu_id = (
    SELECT `id`
    FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0
      AND (`name` = '人力' OR `path` = '/hrm')
      AND `name` <> '人力资源管理'
    ORDER BY CASE WHEN `name` = '人力' THEN 0 ELSE 1 END, `id`
    LIMIT 1
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '人力', '', 1, 25, 0, '/hrm', 'ep:user', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @hrm_menu_id IS NULL;

SET @hrm_menu_id = (
    SELECT `id`
    FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0
      AND (`name` = '人力' OR `path` = '/hrm')
      AND `name` <> '人力资源管理'
    ORDER BY CASE WHEN `name` = '人力' THEN 0 ELSE 1 END, `id`
    LIMIT 1
);

-- 仅校正「人力」根节点字段；不改名成「人力资源管理」
UPDATE `system_menu`
SET `permission` = '', `type` = 1, `parent_id` = 0,
    `path` = '/hrm', `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @hrm_menu_id;

-- 软删平行一级根「人力资源管理」，避免侧栏两套 /hrm
UPDATE `system_menu`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0' AND `parent_id` = 0 AND `name` = '人力资源管理'
  AND `id` <> @hrm_menu_id;

-- 组织架构：将既有「组织架构 → 组织管理/组织架构图」子树挂回活动「人力」根。
-- 旧版菜单曾挂在已软删的「人力资源管理」根下；这里只修正菜单树归属，
-- 具体角色是否可见由角色白名单决定。
SET @organization_menu_id = (
    SELECT `id`
    FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `name` = '组织架构'
      AND `path` = 'org'
    ORDER BY `id`
    LIMIT 1
);
UPDATE `system_menu`
SET `parent_id` = @hrm_menu_id, `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `id` = @organization_menu_id;

UPDATE `system_menu`
SET `parent_id` = @organization_menu_id, `updater` = 'admin', `update_time` = NOW()
WHERE `deleted` = b'0'
  AND @organization_menu_id IS NOT NULL
  AND (
      (`parent_id` = @organization_menu_id AND `name` = '组织管理' AND `component` = 'system/dept/index')
      OR (`parent_id` = @organization_menu_id AND `name` = '组织架构图' AND `component` = 'system/dept/org-chart')
  );

-- 组织管理页下挂 system:dept 按钮，保证父链落在「人力 → 组织架构」内。
-- 系统管理侧 103/1017–1020 仍保留；角色若只绑人力子树，filterDisableMenus
-- 会丢掉父链断裂的按钮，导致 get-permission-info 不含 system:dept:*、前端按钮隐藏。
SET @organization_page_menu_id = (
    SELECT `id`
    FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `parent_id` = @organization_menu_id
      AND `name` = '组织管理'
      AND `component` = 'system/dept/index'
    ORDER BY `id`
    LIMIT 1
);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '组织查询', 'system:dept:query', 3, 1, @organization_page_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @organization_page_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `parent_id` = @organization_page_menu_id
        AND `permission` = 'system:dept:query'
  );
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '组织新增', 'system:dept:create', 3, 2, @organization_page_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @organization_page_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `parent_id` = @organization_page_menu_id
        AND `permission` = 'system:dept:create'
  );
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '组织修改', 'system:dept:update', 3, 3, @organization_page_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @organization_page_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `parent_id` = @organization_page_menu_id
        AND `permission` = 'system:dept:update'
  );
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '组织删除', 'system:dept:delete', 3, 4, @organization_page_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @organization_page_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `parent_id` = @organization_page_menu_id
        AND `permission` = 'system:dept:delete'
  );

-- 复用「人力」下已有二级目录：人事档案 / 人事管理
SET @personnel_archive_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '人事档案' AND `parent_id` = @hrm_menu_id
    ORDER BY `id` LIMIT 1
);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '人事档案', '', 1, 20, @hrm_menu_id, 'personnel-archive', 'ep:folder', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @personnel_archive_menu_id IS NULL;
SET @personnel_archive_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '人事档案' AND `parent_id` = @hrm_menu_id
    ORDER BY `id` LIMIT 1
);

-- 员工档案页：挂到「人力 → 人事档案」下
SET @employee_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee/list/index'
    ORDER BY `id` LIMIT 1
);
SET @legacy_archive_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '员工档案管理'
    ORDER BY `id` LIMIT 1
);
SET @employee_menu_id = COALESCE(@employee_menu_id, @legacy_archive_menu_id);
SET @legacy_archive_list_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @employee_menu_id
      AND `component` = 'hrm/employee-archive/list/index'
    ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '员工档案管理', '', 2, 10, @personnel_archive_menu_id, 'employee', 'ant-design:solution-outlined',
       'hrm/employee/list/index', 'HrmEmployeeArchiveList', 0, b'1', b'1', b'1',
       'admin', NOW(), 'admin', NOW(), b'0'
WHERE @employee_menu_id IS NULL;

SET @employee_menu_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee/list/index'
     ORDER BY `id` LIMIT 1),
    @legacy_archive_menu_id
);

UPDATE `system_menu`
SET `name` = '员工档案管理', `permission` = '', `type` = 2, `sort` = 10,
    `parent_id` = @personnel_archive_menu_id, `path` = 'employee', `icon` = 'ant-design:solution-outlined',
    `component` = 'hrm/employee/list/index', `component_name` = 'HrmEmployeeArchiveList',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @employee_menu_id;

SET @employee_info_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee/info/index'
    ORDER BY `id` LIMIT 1
);
SET @employee_info_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '员工档案详情'
    ORDER BY `id` LIMIT 1
);
SET @employee_info_id = COALESCE(@employee_info_id, @employee_info_by_name_id);

-- 详情必须与列表平级挂在目录下，不可作为列表子节点（否则前端 convert 会清空列表 component，点菜单直达详情/新增）
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '员工档案详情', 'hrm:employee-archive:query', 2, 11, @personnel_archive_menu_id,
       '/hrm/employee/employee-archive-info', '', 'hrm/employee/info/index', 'HrmEmployeeArchiveInfo',
       0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @employee_info_id IS NULL;

SET @employee_info_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee/info/index'
     ORDER BY `id` LIMIT 1),
    @employee_info_by_name_id
);

UPDATE `system_menu`
SET `name` = '员工档案详情', `permission` = 'hrm:employee-archive:query', `type` = 2, `sort` = 11,
    `parent_id` = @personnel_archive_menu_id, `path` = '/hrm/employee/employee-archive-info', `icon` = '',
    `component` = 'hrm/employee/info/index', `component_name` = 'HrmEmployeeArchiveInfo',
    `status` = 0, `visible` = b'0', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @employee_info_id;

-- 旧档案列表是失效路由，软删除，避免动态菜单重复注册
UPDATE `system_menu`
SET `deleted` = b'1', `updater` = 'admin', `update_time` = NOW()
WHERE `id` = @legacy_archive_list_id AND `id` <> COALESCE(@employee_info_id, 0);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT p.`name`, p.`permission`, 3, p.`sort`, @employee_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '员工档案查询' AS `name`, 'hrm:employee-archive:query' AS `permission`, 1 AS `sort`
    UNION ALL SELECT '员工档案创建', 'hrm:employee-archive:create', 2
    UNION ALL SELECT '员工档案更新', 'hrm:employee-archive:update', 3
    UNION ALL SELECT '员工档案删除', 'hrm:employee-archive:delete', 4
    UNION ALL SELECT '员工档案导出', 'hrm:employee-archive:export', 5
) p
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`parent_id` = @employee_menu_id
      AND m.`permission` = p.`permission`
);

-- 人事管理目录（复用「人力」下已有节点）+ 入职/转正/离职/调动
SET @personnel_management_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '人事管理' AND `parent_id` = @hrm_menu_id
    ORDER BY `id` LIMIT 1
);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '人事管理', '', 1, 30, @hrm_menu_id, 'personnel-management', 'ep:user-filled', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @personnel_management_menu_id IS NULL;
SET @personnel_management_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '人事管理' AND `parent_id` = @hrm_menu_id
    ORDER BY `id` LIMIT 1
);
UPDATE `system_menu`
SET `type` = 1, `sort` = 30, `parent_id` = @hrm_menu_id, `path` = 'personnel-management',
    `icon` = 'ep:user-filled', `component` = NULL, `component_name` = NULL, `status` = 0, `visible` = b'1',
    `keep_alive` = b'1', `always_show` = b'1', `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @personnel_management_menu_id;

-- 兼容旧脚本变量名：入职等挂在人事管理下，不再使用「员工关系」中间层
SET @employee_relation_menu_id = @personnel_management_menu_id;

SET @entry_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/entry/list/index'
    ORDER BY `id` LIMIT 1
);
SET @entry_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '入职管理'
    ORDER BY `id` LIMIT 1
);
SET @entry_menu_id = COALESCE(@entry_menu_id, @entry_by_name_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '入职管理', '', 2, 10, @personnel_management_menu_id, 'entry', 'ep:user-filled',
       'hrm/employee-relation/entry/list/index', 'HrmEmployeeEntryBillList',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @entry_menu_id IS NULL;
SET @entry_menu_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/entry/list/index'
     ORDER BY `id` LIMIT 1),
    @entry_by_name_id
);
UPDATE `system_menu`
SET `name` = '入职管理', `permission` = '', `type` = 2, `sort` = 10,
    `parent_id` = @personnel_management_menu_id, `path` = 'entry', `icon` = 'ep:user-filled',
    `component` = 'hrm/employee-relation/entry/list/index', `component_name` = 'HrmEmployeeEntryBillList',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @entry_menu_id;
SET @entry_info_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/entry/info/index'
    ORDER BY `id` LIMIT 1
);
SET @entry_info_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '入职申请详情'
    ORDER BY `id` LIMIT 1
);
SET @entry_info_id = COALESCE(@entry_info_id, @entry_info_by_name_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '入职申请详情', 'hrm:employee-entry-bill:query', 2, 11, @personnel_management_menu_id,
       '/hrm/employee-relation/entry-info', '', 'hrm/employee-relation/entry/info/index', 'HrmEmployeeEntryBillInfo',
       0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @entry_info_id IS NULL;
SET @entry_info_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/entry/info/index'
     ORDER BY `id` LIMIT 1),
    @entry_info_by_name_id
);
UPDATE `system_menu`
SET `name` = '入职申请详情', `permission` = 'hrm:employee-entry-bill:query', `type` = 2, `sort` = 11,
    `parent_id` = @personnel_management_menu_id, `path` = '/hrm/employee-relation/entry-info', `icon` = '',
    `component` = 'hrm/employee-relation/entry/info/index', `component_name` = 'HrmEmployeeEntryBillInfo',
    `status` = 0, `visible` = b'0', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @entry_info_id;
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT p.`name`, p.`permission`, 3, p.`sort`, @entry_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '查询入职申请' AS `name`, 'hrm:employee-entry-bill:query' AS `permission`, 1 AS `sort`
    UNION ALL SELECT '创建入职申请', 'hrm:employee-entry-bill:create', 2
    UNION ALL SELECT '更新入职申请', 'hrm:employee-entry-bill:update', 3
    UNION ALL SELECT '删除入职申请', 'hrm:employee-entry-bill:delete', 4
    UNION ALL SELECT '导出入职申请', 'hrm:employee-entry-bill:export', 5
    UNION ALL SELECT '提交入职申请', 'hrm:employee-entry-bill:submit', 6
    UNION ALL SELECT '撤回入职申请', 'hrm:employee-entry-bill:withdraw', 7
) p
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`parent_id` = @entry_menu_id
      AND m.`permission` = p.`permission`
);

-- 转正管理（父目录 @personnel_management_menu_id 已在入职段就绪）
SET @regular_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/regular/list/index'
    ORDER BY `id` LIMIT 1
);
SET @regular_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '员工转正' AND `parent_id` = @personnel_management_menu_id
    ORDER BY `id` LIMIT 1
);
SET @regular_menu_id = COALESCE(@regular_menu_id, @regular_by_name_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '员工转正', '', 2, 20, @personnel_management_menu_id, 'regular-list', 'ep:user-filled',
       'hrm/employee-relation/regular/list/index', 'HrmEmployeeRegularBillList',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @regular_menu_id IS NULL;
SET @regular_menu_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/regular/list/index'
     ORDER BY `id` LIMIT 1),
    @regular_by_name_id
);
UPDATE `system_menu`
SET `name` = '员工转正', `permission` = '', `type` = 2, `sort` = 20,
    `parent_id` = @personnel_management_menu_id, `path` = 'regular-list', `icon` = 'ep:user-filled',
    `component` = 'hrm/employee-relation/regular/list/index', `component_name` = 'HrmEmployeeRegularBillList',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @regular_menu_id;
SET @regular_info_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/regular/info/index'
    ORDER BY `id` LIMIT 1
);
SET @regular_info_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '转正申请详情'
    ORDER BY `id` LIMIT 1
);
SET @regular_info_id = COALESCE(@regular_info_id, @regular_info_by_name_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '转正申请详情', 'hrm:employee-regular-bill:query', 2, 21, @personnel_management_menu_id,
       '/hrm/employee-relation/regular-info', '', 'hrm/employee-relation/regular/info/index', 'HrmEmployeeRegularBillInfo',
       0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @regular_info_id IS NULL;
SET @regular_info_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/regular/info/index'
     ORDER BY `id` LIMIT 1),
    @regular_info_by_name_id
);
UPDATE `system_menu`
SET `name` = '转正申请详情', `permission` = 'hrm:employee-regular-bill:query', `type` = 2, `sort` = 21,
    `parent_id` = @personnel_management_menu_id, `path` = '/hrm/employee-relation/regular-info', `icon` = '',
    `component` = 'hrm/employee-relation/regular/info/index', `component_name` = 'HrmEmployeeRegularBillInfo',
    `status` = 0, `visible` = b'0', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @regular_info_id;
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT p.`name`, p.`permission`, 3, p.`sort`, @regular_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '查询转正申请' AS `name`, 'hrm:employee-regular-bill:query' AS `permission`, 1 AS `sort`
    UNION ALL SELECT '创建转正申请', 'hrm:employee-regular-bill:create', 2
    UNION ALL SELECT '更新转正申请', 'hrm:employee-regular-bill:update', 3
    UNION ALL SELECT '删除转正申请', 'hrm:employee-regular-bill:delete', 4
    UNION ALL SELECT '导出转正申请', 'hrm:employee-regular-bill:export', 5
    UNION ALL SELECT '提交转正申请', 'hrm:employee-regular-bill:submit', 6
    UNION ALL SELECT '撤回转正申请', 'hrm:employee-regular-bill:withdraw', 7
) p
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`parent_id` = @regular_menu_id
      AND m.`permission` = p.`permission`
);

-- 人事离职管理
SET @resignation_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/resignation/list/index'
    ORDER BY `id` LIMIT 1
);
SET @resignation_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '员工离职' AND `parent_id` = @personnel_management_menu_id
    ORDER BY `id` LIMIT 1
);
SET @resignation_menu_id = COALESCE(@resignation_menu_id, @resignation_by_name_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '员工离职', '', 2, 40, @personnel_management_menu_id, 'resignation-list', 'ep:user-filled',
       'hrm/employee-relation/resignation/list/index', 'HrmEmployeeResignationBillList',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @resignation_menu_id IS NULL;
SET @resignation_menu_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/resignation/list/index'
     ORDER BY `id` LIMIT 1),
    @resignation_by_name_id
);
UPDATE `system_menu`
SET `name` = '员工离职', `permission` = '', `type` = 2, `sort` = 40,
    `parent_id` = @personnel_management_menu_id, `path` = 'resignation-list', `icon` = 'ep:user-filled',
    `component` = 'hrm/employee-relation/resignation/list/index', `component_name` = 'HrmEmployeeResignationBillList',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @resignation_menu_id;
SET @resignation_info_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/resignation/info/index'
    ORDER BY `id` LIMIT 1
);
SET @resignation_info_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '离职申请详情'
    ORDER BY `id` LIMIT 1
);
SET @resignation_info_id = COALESCE(@resignation_info_id, @resignation_info_by_name_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '离职申请详情', 'hrm:employee-resignation-bill:query', 2, 41, @personnel_management_menu_id,
       '/hrm/employee-relation/resignation-info', '', 'hrm/employee-relation/resignation/info/index',
       'HrmEmployeeResignationBillInfo', 0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @resignation_info_id IS NULL;
SET @resignation_info_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/resignation/info/index'
     ORDER BY `id` LIMIT 1),
    @resignation_info_by_name_id
);
UPDATE `system_menu`
SET `name` = '离职申请详情', `permission` = 'hrm:employee-resignation-bill:query', `type` = 2, `sort` = 41,
    `parent_id` = @personnel_management_menu_id, `path` = '/hrm/employee-relation/resignation-info', `icon` = '',
    `component` = 'hrm/employee-relation/resignation/info/index',
    `component_name` = 'HrmEmployeeResignationBillInfo', `status` = 0, `visible` = b'0',
    `keep_alive` = b'1', `always_show` = b'1', `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @resignation_info_id;
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT p.`name`, p.`permission`, 3, p.`sort`, @resignation_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '查询离职申请' AS `name`, 'hrm:employee-resignation-bill:query' AS `permission`, 1 AS `sort`
    UNION ALL SELECT '创建离职申请', 'hrm:employee-resignation-bill:create', 2
    UNION ALL SELECT '更新离职申请', 'hrm:employee-resignation-bill:update', 3
    UNION ALL SELECT '删除离职申请', 'hrm:employee-resignation-bill:delete', 4
    UNION ALL SELECT '导出离职申请', 'hrm:employee-resignation-bill:export', 5
    UNION ALL SELECT '提交离职申请', 'hrm:employee-resignation-bill:submit', 6
    UNION ALL SELECT '撤回离职申请', 'hrm:employee-resignation-bill:withdraw', 7
) p
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`parent_id` = @resignation_menu_id
      AND m.`permission` = p.`permission`
);

-- 人事调动管理
SET @transfer_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/transfer/list/index'
    ORDER BY `id` LIMIT 1
);
SET @transfer_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '人事调动' AND `parent_id` = @personnel_management_menu_id
    ORDER BY `id` LIMIT 1
);
SET @transfer_menu_id = COALESCE(@transfer_menu_id, @transfer_by_name_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '人事调动', '', 2, 50, @personnel_management_menu_id, 'transfer-list', 'ep:user-filled',
       'hrm/employee-relation/transfer/list/index', 'HrmEmployeeTransferBillList',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @transfer_menu_id IS NULL;
SET @transfer_menu_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/transfer/list/index'
     ORDER BY `id` LIMIT 1),
    @transfer_by_name_id
);
UPDATE `system_menu`
SET `name` = '人事调动', `permission` = '', `type` = 2, `sort` = 50,
    `parent_id` = @personnel_management_menu_id, `path` = 'transfer-list', `icon` = 'ep:user-filled',
    `component` = 'hrm/employee-relation/transfer/list/index', `component_name` = 'HrmEmployeeTransferBillList',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @transfer_menu_id;
SET @transfer_info_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/transfer/info/index'
    ORDER BY `id` LIMIT 1
);
SET @transfer_info_by_name_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `name` = '调动申请详情'
    ORDER BY `id` LIMIT 1
);
SET @transfer_info_id = COALESCE(@transfer_info_id, @transfer_info_by_name_id);
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '调动申请详情', 'hrm:employee-transfer-bill:query', 2, 51, @personnel_management_menu_id,
       '/hrm/employee-relation/transfer-info', '', 'hrm/employee-relation/transfer/info/index', 'HrmEmployeeTransferBillInfo',
       0, b'0', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @transfer_info_id IS NULL;
SET @transfer_info_id = COALESCE(
    (SELECT `id` FROM `system_menu`
     WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/transfer/info/index'
     ORDER BY `id` LIMIT 1),
    @transfer_info_by_name_id
);
UPDATE `system_menu`
SET `name` = '调动申请详情', `permission` = 'hrm:employee-transfer-bill:query', `type` = 2, `sort` = 51,
    `parent_id` = @personnel_management_menu_id, `path` = '/hrm/employee-relation/transfer-info', `icon` = '',
    `component` = 'hrm/employee-relation/transfer/info/index', `component_name` = 'HrmEmployeeTransferBillInfo',
    `status` = 0, `visible` = b'0', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = 'admin', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = @transfer_info_id;
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT p.`name`, p.`permission`, 3, p.`sort`, @transfer_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '查询调动申请' AS `name`, 'hrm:employee-transfer-bill:query' AS `permission`, 1 AS `sort`
    UNION ALL SELECT '创建调动申请', 'hrm:employee-transfer-bill:create', 2
    UNION ALL SELECT '更新调动申请', 'hrm:employee-transfer-bill:update', 3
    UNION ALL SELECT '删除调动申请', 'hrm:employee-transfer-bill:delete', 4
    UNION ALL SELECT '导出调动申请', 'hrm:employee-transfer-bill:export', 5
    UNION ALL SELECT '提交调动申请', 'hrm:employee-transfer-bill:submit', 6
    UNION ALL SELECT '撤回调动申请', 'hrm:employee-transfer-bill:withdraw', 7
) p
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`parent_id` = @transfer_menu_id
      AND m.`permission` = p.`permission`
);

-- 软删残留中间层：旧「员工关系」空目录（MySQL 禁止同表 UPDATE 子查询，拆两步）
DROP TEMPORARY TABLE IF EXISTS tmp_empty_employee_relation;
CREATE TEMPORARY TABLE tmp_empty_employee_relation AS
SELECT m.`id`
FROM `system_menu` m
WHERE m.`deleted` = b'0' AND m.`name` = '员工关系'
  AND m.`id` <> COALESCE(@personnel_management_menu_id, 0)
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu` c
      WHERE c.`deleted` = b'0' AND c.`parent_id` = m.`id`
  );
UPDATE `system_menu` m
JOIN tmp_empty_employee_relation t ON t.`id` = m.`id`
SET m.`deleted` = b'1', m.`updater` = 'admin', m.`update_time` = NOW();
DROP TEMPORARY TABLE IF EXISTS tmp_empty_employee_relation;

-- common 是 tenant 1 的普通角色；超级管理员本身拥有全部菜单，无需额外写入。
INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`status` = 0 AND r.`code` = 'common' AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
       m.`id` IN (
           @hrm_menu_id, @personnel_archive_menu_id,
           @employee_menu_id, @employee_info_id,
           @personnel_management_menu_id, @entry_menu_id, @entry_info_id,
           @regular_menu_id, @regular_info_id,
           @resignation_menu_id, @resignation_info_id, @transfer_menu_id, @transfer_info_id
       )
       OR m.`permission` IN (
           'hrm:employee-archive:query', 'hrm:employee-archive:create',
           'hrm:employee-archive:update', 'hrm:employee-archive:delete',
           'hrm:employee-archive:export',
           'hrm:employee-entry-bill:query', 'hrm:employee-entry-bill:create',
           'hrm:employee-entry-bill:update', 'hrm:employee-entry-bill:delete',
           'hrm:employee-entry-bill:export', 'hrm:employee-entry-bill:submit',
           'hrm:employee-entry-bill:withdraw',
           'hrm:employee-regular-bill:query', 'hrm:employee-regular-bill:create',
           'hrm:employee-regular-bill:update', 'hrm:employee-regular-bill:delete',
           'hrm:employee-regular-bill:export', 'hrm:employee-regular-bill:submit',
           'hrm:employee-regular-bill:withdraw',
           'hrm:employee-resignation-bill:query', 'hrm:employee-resignation-bill:create',
           'hrm:employee-resignation-bill:update', 'hrm:employee-resignation-bill:delete',
           'hrm:employee-resignation-bill:export', 'hrm:employee-resignation-bill:submit',
           'hrm:employee-resignation-bill:withdraw',
           'hrm:employee-transfer-bill:query', 'hrm:employee-transfer-bill:create',
           'hrm:employee-transfer-bill:update', 'hrm:employee-transfer-bill:delete',
           'hrm:employee-transfer-bill:export', 'hrm:employee-transfer-bill:submit',
           'hrm:employee-transfer-bill:withdraw'
       )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
        AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );

COMMIT;
