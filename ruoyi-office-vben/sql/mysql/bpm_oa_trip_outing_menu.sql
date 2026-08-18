-- OA 出差/外出菜单权限（幂等；挂在请假同一 BPM OA 父级下，非 finance）
-- 按钮仅 query + create。query 授予 hr_admin / super_admin；不授予专门的 create 角色。

SET NAMES utf8mb4;

-- 出差查询（父级与请假查询相同）
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '出差查询', '', 2, 1,
       (SELECT `parent_id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/oa/leave/index' LIMIT 1),
       'trip', 'fa:plane', 'bpm/oa/trip/index', 'BpmOATrip',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE (SELECT `parent_id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/oa/leave/index' LIMIT 1) IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'bpm/oa/trip/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '出差申请查询' AS name, 'bpm:oa-trip:query' AS permission, 1 AS sort
    UNION ALL SELECT '出差申请创建', 'bpm:oa-trip:create', 2
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'bpm/oa/trip/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- 外出查询（父级与请假查询相同）
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '外出查询', '', 2, 2,
       (SELECT `parent_id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/oa/leave/index' LIMIT 1),
       'outing', 'fa:sign-out', 'bpm/oa/outing/index', 'BpmOAOuting',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE (SELECT `parent_id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/oa/leave/index' LIMIT 1) IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'bpm/oa/outing/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '外出申请查询' AS name, 'bpm:oa-outing:query' AS permission, 1 AS sort
    UNION ALL SELECT '外出申请创建', 'bpm:oa-outing:create', 2
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'bpm/oa/outing/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m
    WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

-- hr_admin / super_admin：页面 + query（不含 create）
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` IN ('hr_admin', 'super_admin') AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (
        m.`id` = (
            SELECT `parent_id` FROM `system_menu`
            WHERE `deleted` = b'0' AND `component` = 'bpm/oa/leave/index' LIMIT 1
        )
     OR m.`component` IN ('bpm/oa/trip/index', 'bpm/oa/outing/index')
     OR m.`permission` IN ('bpm:oa-trip:query', 'bpm:oa-outing:query')
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
