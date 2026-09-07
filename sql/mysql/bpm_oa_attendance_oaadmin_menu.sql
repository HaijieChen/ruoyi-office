-- 把加班/补卡/节假日日历菜单授给 oaadmin 当前角色和 tenant_admin（幂等）。
-- 查询/核验按钮，不含 create。员工发起走假勤目录，不依赖这些查询菜单。
-- 节假日日历与加班查询同级，地址 /bpm/oa/overtime/calendar。不要挂在加班查询页面下。

SET NAMES utf8mb4;

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`tenant_id` = 1
  AND (
        r.`code` = 'tenant_admin'
     OR r.`id` IN (
            SELECT ur.`role_id`
            FROM `system_user_role` ur
            JOIN `system_users` u ON u.`id` = ur.`user_id`
            WHERE u.`username` = 'oaadmin'
              AND u.`deleted` = b'0'
              AND u.`tenant_id` = 1
              AND ur.`deleted` = b'0'
              AND ur.`tenant_id` = 1
        )
  )
  AND m.`deleted` = b'0'
  AND (
        m.`id` = (
            SELECT `parent_id` FROM `system_menu`
            WHERE `deleted` = b'0' AND `component` = 'bpm/oa/leave/index' LIMIT 1
        )
     OR m.`component` IN (
            'bpm/oa/overtime/index',
            'bpm/oa/punch/index',
            'bpm/oa/overtime-calendar/index'
        )
     OR m.`permission` IN (
            'bpm:oa-overtime:query',
            'bpm:oa-punch-correction:query',
            'bpm:oa-overtime-calendar:query',
            'bpm:oa-overtime-calendar:verify'
        )
  )
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
