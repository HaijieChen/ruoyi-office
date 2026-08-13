-- EXP-86：组织架构导入权限 system:dept:import
-- 幂等：可重复执行。
-- 1) 在「人力 → 组织架构 → 组织管理」下挂导入按钮（前端 auth 可见）
-- 2) 在系统管理侧部门菜单（parent_id=103）下挂同权按钮（兼容旧入口）
-- 3) 仅授权 hr_admin；不授予 finance_admin / business_staff
--
-- 前置：hrm_menu_open.sql 已创建组织管理页；system_dept.functional_currency 列已存在（EXP-73）。
--
-- 部署/回滚（C1 + R2-FINAL-01 缓存，应用侧，无 DDL）：
-- - 滚动发布：允许新旧包写者并存。
--   * 新包读写 dept_children_ids_v2（GenerationStampedSet）+ 代际；
--     在遗留名 dept_children_ids 写入 Long 型 epoch 标记键 __dept_children_v2_epoch__。
--   * 旧包读写 dept_children_ids（Set），@CacheEvict(allEntries) 会清掉 epoch 标记；
--     新包读路径发现标记缺失 → bump 代际并清空 V2 → 拒绝陈旧 V2 命中（旧写→新读闭合）。
--   * 新 stamped 类型绝不写入遗留名 → 旧实例无 SerializationException（C1）。
-- - 回滚应用：停新包后仅旧包读遗留名；V2 键可残留至 TTL，旧包不访问。
-- - 生产需 Redisson（租户写锁 + 代际 AtomicLong）。
-- - 发布前仍执行本权限脚本 + EXP-73 列预检。

SET NAMES utf8mb4;

-- ========== 1. 人力侧组织管理页按钮 ==========
SET @organization_page_menu_id = (
    SELECT `id`
    FROM `system_menu`
    WHERE `deleted` = b'0'
      AND `name` = '组织管理'
      AND `component` = 'system/dept/index'
      AND `parent_id` IN (
          SELECT `id` FROM `system_menu`
          WHERE `deleted` = b'0' AND `name` = '组织架构'
      )
    ORDER BY `id`
    LIMIT 1
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '组织导入', 'system:dept:import', 3, 5, @organization_page_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @organization_page_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `parent_id` = @organization_page_menu_id
        AND `permission` = 'system:dept:import'
  );

-- ========== 2. 系统管理侧部门菜单按钮（parent=103 若存在） ==========
SET @system_dept_menu_id = (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `id` = 103
    LIMIT 1
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '部门导入', 'system:dept:import', 3, 5, @system_dept_menu_id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @system_dept_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_menu`
      WHERE `deleted` = b'0' AND `parent_id` = @system_dept_menu_id
        AND `permission` = 'system:dept:import'
  );

-- ========== 3. 仅绑定 hr_admin ==========
SET @hr_admin_role_id = (
    SELECT MIN(`id`) FROM `system_role`
    WHERE `deleted` = b'0' AND `code` = 'hr_admin' AND `tenant_id` = 1
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT @hr_admin_role_id, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_menu` m
WHERE m.`deleted` = b'0'
  AND m.`permission` = 'system:dept:import'
  AND @hr_admin_role_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`deleted` = b'0' AND rm.`tenant_id` = 1
        AND rm.`role_id` = @hr_admin_role_id AND rm.`menu_id` = m.`id`
  );
