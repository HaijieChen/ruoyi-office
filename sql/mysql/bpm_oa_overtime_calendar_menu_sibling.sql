-- 生产热修：节假日日历从「加班查询」页面下挪到 OA 目录同级。
-- 挂在加班查询(type=2 有 component)下会清空加班组件并 redirect 到日历，菜单打开 404。
-- 幂等。执行后管理员重新登录即可，不必切应用。

SET NAMES utf8mb4;

UPDATE `system_menu` m
JOIN (
    SELECT `parent_id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'bpm/oa/leave/index' LIMIT 1
) oa ON 1=1
SET m.`parent_id` = oa.`parent_id`, m.`path` = 'overtime/calendar'
WHERE m.`deleted` = b'0' AND m.`component` = 'bpm/oa/overtime-calendar/index';
