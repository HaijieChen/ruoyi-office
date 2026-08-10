-- 人事管理员角色与菜单权限（幂等、有效状态可重复收敛）
--
-- 执行顺序：
--   1) workbench_role_menu_home.sql（工作台，必须先完成 /dashboard -> /home 归属）
--   2) hrm_menu_open.sql（人力菜单树）
--   3) 本脚本（角色及角色菜单绑定）
--   4) hrm_test_user_hr_admin.sql（本地验证账号）
--
-- 菜单白名单：
--   工作台：/dashboard + /home（dashboard/home/index）。首页归属只依赖前置迁移，本脚本不修改 system_menu。
--   基础流程：/bpm、任务目录、我的流程、待办、已办、抄送、发起及下方 bpm:* 办理按钮。
--   人力：/hrm、人事档案/员工档案、人事管理下的入职/转正/离职/调动、组织架构下的组织管理/架构图及对应按钮。
--   组织架构只开放 system:dept:*；不包含系统管理根、用户/角色/岗位/租户、流程模型/表单配置、考勤及其他未列出的业务菜单。
--
-- data_scope=1（全部数据）沿用当前业务假设，仍待业务负责人确认；本脚本不因流程发起而授予模型管理权限。
-- 角色菜单链接采用先删后插：有效权限状态可重复收敛，但链接自增 id 可能变化，不承诺物理行 id 不变。
--
-- 关键菜单 selector 必须恰好命中 1 条；不满足时用 SIGNAL 回滚，防止重复逻辑键或缺前置时静默选首条/漏绑。

SET NAMES utf8mb4;

-- mysql 客户端执行成功后删除；失败时保留到下一次重跑开头再删除，不影响事务回滚。
DROP PROCEDURE IF EXISTS `__tmp_hr_admin_assert_single`;
DELIMITER $$
CREATE PROCEDURE `__tmp_hr_admin_assert_single`(IN p_selector VARCHAR(96), IN p_count BIGINT)
BEGIN
    DECLARE v_message VARCHAR(255);
    IF p_count <> 1 THEN
        ROLLBACK;
        SET v_message = CONCAT('hr_admin selector must match exactly one active row: ', p_selector,
                               ' (count=', p_count, ')');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
    END IF;
END$$
DELIMITER ;

START TRANSACTION;
SET @tenant_id = 1;

-- ========== 1. 解析并严格校验前置菜单 ==========
-- 工作台：只读既有迁移创建/校正后的最终形态
SET @dashboard_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` = '/dashboard'
);
SET @dashboard_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` = '/dashboard'
);
CALL `__tmp_hr_admin_assert_single`('dashboard root /dashboard', @dashboard_menu_count);

SET @dashboard_home_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0' AND `path` = '/home' AND `component` = 'dashboard/home/index'
);
SET @dashboard_home_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0' AND `path` = '/home' AND `component` = 'dashboard/home/index'
);
CALL `__tmp_hr_admin_assert_single`('dashboard home /home', @dashboard_home_menu_count);

SET @dashboard_home_parent_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `id` = @dashboard_home_menu_id AND `parent_id` = @dashboard_menu_id
);
CALL `__tmp_hr_admin_assert_single`('dashboard home parent /dashboard', @dashboard_home_parent_count);

-- 基础流程：只取审批办理链路，不取流程管理配置
SET @bpm_root_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` = '/bpm'
);
SET @bpm_root_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` = '/bpm'
);
CALL `__tmp_hr_admin_assert_single`('bpm root /bpm', @bpm_root_menu_count);

SET @bpm_task_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @bpm_root_menu_id AND `path` = 'task'
);
SET @bpm_task_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @bpm_root_menu_id AND `path` = 'task'
);
CALL `__tmp_hr_admin_assert_single`('bpm task directory', @bpm_task_menu_count);

SET @bpm_my_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/processInstance/index');
SET @bpm_my_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/processInstance/index');
CALL `__tmp_hr_admin_assert_single`('bpm my process page', @bpm_my_menu_count);

SET @bpm_todo_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/task/todo/index');
SET @bpm_todo_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/task/todo/index');
CALL `__tmp_hr_admin_assert_single`('bpm todo page', @bpm_todo_menu_count);

SET @bpm_done_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/task/done/index');
SET @bpm_done_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/task/done/index');
CALL `__tmp_hr_admin_assert_single`('bpm done page', @bpm_done_menu_count);

SET @bpm_copy_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/task/copy/index');
SET @bpm_copy_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/task/copy/index');
CALL `__tmp_hr_admin_assert_single`('bpm copy page', @bpm_copy_menu_count);

SET @bpm_start_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/processInstance/create/index');
SET @bpm_start_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/processInstance/create/index');
CALL `__tmp_hr_admin_assert_single`('bpm start process page', @bpm_start_menu_count);

-- 人力：依赖 hrm_menu_open.sql 的最终菜单树；自增 menuId 仍通过稳定键解析
SET @hrm_root_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` = '/hrm'
);
SET @hrm_root_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = 0 AND `path` = '/hrm'
);
CALL `__tmp_hr_admin_assert_single`('hrm root /hrm', @hrm_root_menu_count);

SET @hrm_archive_dir_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @hrm_root_menu_id AND `path` = 'personnel-archive'
);
SET @hrm_archive_dir_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @hrm_root_menu_id AND `path` = 'personnel-archive'
);
CALL `__tmp_hr_admin_assert_single`('hrm personnel archive directory', @hrm_archive_dir_menu_count);

SET @hrm_employee_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee/list/index');
SET @hrm_employee_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee/list/index');
CALL `__tmp_hr_admin_assert_single`('hrm employee list page', @hrm_employee_menu_count);

SET @hrm_employee_info_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee/info/index');
SET @hrm_employee_info_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee/info/index');
CALL `__tmp_hr_admin_assert_single`('hrm employee detail page', @hrm_employee_info_menu_count);

SET @hrm_management_dir_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @hrm_root_menu_id AND `path` = 'personnel-management'
);
SET @hrm_management_dir_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0' AND `parent_id` = @hrm_root_menu_id AND `path` = 'personnel-management'
);
CALL `__tmp_hr_admin_assert_single`('hrm personnel management directory', @hrm_management_dir_menu_count);

-- 组织架构：依赖 hrm_menu_open.sql 将既有子树挂到活动 /hrm 根下
SET @hrm_organization_dir_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `parent_id` = @hrm_root_menu_id
      AND `name` = '组织架构'
      AND `path` = 'org'
);
SET @hrm_organization_dir_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `parent_id` = @hrm_root_menu_id
      AND `name` = '组织架构'
      AND `path` = 'org'
);
CALL `__tmp_hr_admin_assert_single`('hrm organization directory', @hrm_organization_dir_menu_count);

SET @hrm_organization_page_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `parent_id` = @hrm_organization_dir_menu_id
      AND `name` = '组织管理'
      AND `component` = 'system/dept/index'
);
SET @hrm_organization_page_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `parent_id` = @hrm_organization_dir_menu_id
      AND `name` = '组织管理'
      AND `component` = 'system/dept/index'
);
CALL `__tmp_hr_admin_assert_single`('hrm organization management page', @hrm_organization_page_menu_count);

SET @hrm_organization_chart_menu_count = (
    SELECT COUNT(*) FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `parent_id` = @hrm_organization_dir_menu_id
      AND `name` = '组织架构图'
      AND `component` = 'system/dept/org-chart'
);
SET @hrm_organization_chart_menu_id = (
    SELECT MIN(`id`) FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `parent_id` = @hrm_organization_dir_menu_id
      AND `name` = '组织架构图'
      AND `component` = 'system/dept/org-chart'
);
CALL `__tmp_hr_admin_assert_single`('hrm organization chart page', @hrm_organization_chart_menu_count);

SET @hrm_entry_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/entry/list/index');
SET @hrm_entry_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/entry/list/index');
CALL `__tmp_hr_admin_assert_single`('hrm entry list page', @hrm_entry_menu_count);
SET @hrm_entry_info_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/entry/info/index');
SET @hrm_entry_info_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/entry/info/index');
CALL `__tmp_hr_admin_assert_single`('hrm entry detail page', @hrm_entry_info_menu_count);

SET @hrm_regular_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/regular/list/index');
SET @hrm_regular_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/regular/list/index');
CALL `__tmp_hr_admin_assert_single`('hrm regular list page', @hrm_regular_menu_count);
SET @hrm_regular_info_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/regular/info/index');
SET @hrm_regular_info_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/regular/info/index');
CALL `__tmp_hr_admin_assert_single`('hrm regular detail page', @hrm_regular_info_menu_count);

SET @hrm_resignation_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/resignation/list/index');
SET @hrm_resignation_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/resignation/list/index');
CALL `__tmp_hr_admin_assert_single`('hrm resignation list page', @hrm_resignation_menu_count);
SET @hrm_resignation_info_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/resignation/info/index');
SET @hrm_resignation_info_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/resignation/info/index');
CALL `__tmp_hr_admin_assert_single`('hrm resignation detail page', @hrm_resignation_info_menu_count);

SET @hrm_transfer_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/transfer/list/index');
SET @hrm_transfer_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/transfer/list/index');
CALL `__tmp_hr_admin_assert_single`('hrm transfer list page', @hrm_transfer_menu_count);
SET @hrm_transfer_info_menu_count = (SELECT COUNT(*) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/transfer/info/index');
SET @hrm_transfer_info_menu_id = (SELECT MIN(`id`) FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'hrm/employee-relation/transfer/info/index');
CALL `__tmp_hr_admin_assert_single`('hrm transfer detail page', @hrm_transfer_info_menu_count);

-- ========== 2. 创建/恢复角色 ==========
UPDATE `system_role`
SET `name` = '人事管理员',
    `sort` = 40,
    `data_scope` = 1,
    `data_scope_dept_ids` = '',
    `status` = 0,
    `type` = 2,
    `remark` = '人事管理员：工作台、基础流程办理、员工全生命周期及组织架构维护；不含系统管理与流程配置权限。',
    `updater` = 'admin',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `code` = 'hr_admin' AND `tenant_id` = @tenant_id;

INSERT INTO `system_role`
    (`name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '人事管理员', 'hr_admin', 40, 1, '', 0, 2,
       '人事管理员：工作台、基础流程办理、员工全生命周期及组织架构维护；不含系统管理与流程配置权限。',
       'admin', NOW(), 'admin', NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM `system_role`
    WHERE `code` = 'hr_admin' AND `tenant_id` = @tenant_id
);

SET @hr_admin_role_count = (
    SELECT COUNT(*) FROM `system_role`
    WHERE `deleted` = b'0' AND `code` = 'hr_admin' AND `tenant_id` = @tenant_id
);
CALL `__tmp_hr_admin_assert_single`('hr_admin role', @hr_admin_role_count);
SET @hr_admin_role_id = (
    SELECT MIN(`id`) FROM `system_role`
    WHERE `deleted` = b'0' AND `code` = 'hr_admin' AND `tenant_id` = @tenant_id
);

-- ========== 3. 角色菜单白名单（先清后插，保证有效状态重复收敛） ==========
DELETE FROM `system_role_menu`
WHERE `tenant_id` = @tenant_id AND `role_id` = @hr_admin_role_id;

INSERT INTO `system_role_menu`
    (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', @tenant_id
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`id` = @hr_admin_role_id
  AND r.`deleted` = b'0'
  AND r.`tenant_id` = @tenant_id
  AND m.`deleted` = b'0'
  AND (
        -- 工作台
        m.`id` IN (
            @dashboard_menu_id, @dashboard_home_menu_id,
            -- 基础流程页面/目录
            @bpm_root_menu_id, @bpm_task_menu_id, @bpm_my_menu_id, @bpm_todo_menu_id,
            @bpm_done_menu_id, @bpm_copy_menu_id, @bpm_start_menu_id,
            -- 人力页面/目录
            @hrm_root_menu_id, @hrm_archive_dir_menu_id, @hrm_employee_menu_id,
            @hrm_employee_info_menu_id, @hrm_management_dir_menu_id,
            @hrm_organization_dir_menu_id, @hrm_organization_page_menu_id,
            @hrm_organization_chart_menu_id,
            @hrm_entry_menu_id, @hrm_entry_info_menu_id,
            @hrm_regular_menu_id, @hrm_regular_info_menu_id,
            @hrm_resignation_menu_id, @hrm_resignation_info_menu_id,
            @hrm_transfer_menu_id, @hrm_transfer_info_menu_id
        )
        -- 组织架构维护按钮（不授予系统管理根/用户/角色/岗位/租户菜单）
     OR m.`permission` IN (
            'system:dept:query',
            'system:dept:create',
            'system:dept:update',
            'system:dept:delete'
        )
        -- 流程办理按钮
     OR m.`permission` IN (
            'bpm:process-instance:query',
            'bpm:process-instance:create',
            'bpm:process-instance:cancel',
            'bpm:process-instance-cc:query',
            'bpm:task:query',
            'bpm:task:update'
        )
        -- 人力按钮：员工档案、入职、转正、离职、调动
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
  );

COMMIT;
DROP PROCEDURE IF EXISTS `__tmp_hr_admin_assert_single`;
