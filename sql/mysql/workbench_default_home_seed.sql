-- =============================================
-- WB-T2：default_workspace 四组件布局幂等种子
-- 仅当该 page 尚无 layout 行时插入；已有运营配置不覆盖
-- 组件 code 与前端 registry.ts 一致
-- =============================================

SET NAMES utf8mb4;

SET @tenant_id = 1;

-- ---------- 1. 默认首页页头（缺则建；有则不改运营字段）----------
-- status 使用 CommonStatusEnum.ENABLE = 0（开启）
INSERT INTO `system_home_page`
    (`name`, `code`, `description`, `preview_image`, `is_default`, `status`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT '默认工作台', 'default_workspace', '系统默认可配首页（欢迎/待办/公告/快捷入口）',
       NULL, 1, 0, 0,
       '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM `system_home_page`
    WHERE `deleted` = b'0' AND `code` = 'default_workspace'
);

SET @page_id = (
    SELECT `id` FROM `system_home_page`
    WHERE `deleted` = b'0' AND `code` = 'default_workspace'
    ORDER BY `id` ASC LIMIT 1
);

-- ---------- 2. 组件元数据（缺则补；不改已有 config_schema）----------
-- status=0 对齐 CommonStatusEnum.ENABLE

-- workbench_welcome
INSERT INTO `system_home_component`
    (`category_id`, `name`, `code`, `component_path`, `description`, `preview_image`,
     `default_width`, `default_height`, `config_schema`, `status`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 2, '欢迎组件', 'workbench_welcome',
       'dashboard/home/components/welcome/workbench-welcome.vue',
       '展示欢迎信息、用户信息和天气', NULL,
       24, 4,
       '{"properties":[{"key":"showWeather","type":"boolean","label":"显示天气","default":false}]}',
       0, 10, '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM `system_home_component` WHERE `deleted` = b'0' AND `code` = 'workbench_welcome'
);

-- workbench_task_list
INSERT INTO `system_home_component`
    (`category_id`, `name`, `code`, `component_path`, `description`, `preview_image`,
     `default_width`, `default_height`, `config_schema`, `status`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 2, '任务列表', 'workbench_task_list',
       'dashboard/home/components/taskLists/workbench-task-list.vue',
       '展示我的单据、待办任务、已办任务、抄送我的', NULL,
       24, 8,
       '{"properties":[{"key":"maxRecordNum","type":"number","label":"最大条数","default":10}]}',
       0, 20, '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM `system_home_component` WHERE `deleted` = b'0' AND `code` = 'workbench_task_list'
);

-- workbench_notice
INSERT INTO `system_home_component`
    (`category_id`, `name`, `code`, `component_path`, `description`, `preview_image`,
     `default_width`, `default_height`, `config_schema`, `status`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 2, '通知公告', 'workbench_notice',
       'dashboard/home/components/notice/workbench-notice.vue',
       '展示系统通知公告列表', NULL,
       12, 8,
       '{"properties":[{"key":"maxRecordNum","type":"number","label":"最大条数","default":10}]}',
       0, 30, '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM `system_home_component` WHERE `deleted` = b'0' AND `code` = 'workbench_notice'
);

-- workbench_quick_nav（items 由 WB-T4 写入布局 config；组件侧读 config）
INSERT INTO `system_home_component`
    (`category_id`, `name`, `code`, `component_path`, `description`, `preview_image`,
     `default_width`, `default_height`, `config_schema`, `status`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 2, '快捷导航', 'workbench_quick_nav',
       'dashboard/home/components/navigation/workbench-quick-nav.vue',
       '展示快捷导航入口', NULL,
       24, 5,
       '{"properties":[{"key":"title","type":"string","label":"标题","default":"快捷导航"},{"key":"items","type":"json","label":"入口列表"}]}',
       0, 40, '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE NOT EXISTS (
    SELECT 1 FROM `system_home_component` WHERE `deleted` = b'0' AND `code` = 'workbench_quick_nav'
);

-- 确保四组件为启用（若曾被停用）
UPDATE `system_home_component`
SET `status` = 0, `updater` = '1', `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `code` IN ('workbench_welcome', 'workbench_task_list', 'workbench_notice', 'workbench_quick_nav')
  AND `status` <> 0;

-- ---------- 3. 默认布局：仅当 page 尚无任何 layout 行时插入 ----------
-- quick_nav config 先占位；WB-T4 会写入 items（可重复跑本段外的 T4 SQL 更新）

SET @layout_cnt = (
    SELECT COUNT(1) FROM `system_home_page_layout`
    WHERE `deleted` = b'0' AND `page_id` = @page_id
);

-- sort0 welcome
INSERT INTO `system_home_page_layout`
    (`page_id`, `component_code`, `position_x`, `position_y`, `width`, `height`, `config`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT @page_id, 'workbench_welcome', 0, 0, 24, 4, '{"showWeather":false}', 0,
       '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE @page_id IS NOT NULL AND @layout_cnt = 0;

-- sort1 task_list
INSERT INTO `system_home_page_layout`
    (`page_id`, `component_code`, `position_x`, `position_y`, `width`, `height`, `config`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT @page_id, 'workbench_task_list', 0, 4, 16, 8, '{"maxRecordNum":10}', 1,
       '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE @page_id IS NOT NULL AND @layout_cnt = 0;

-- sort2 notice
INSERT INTO `system_home_page_layout`
    (`page_id`, `component_code`, `position_x`, `position_y`, `width`, `height`, `config`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT @page_id, 'workbench_notice', 16, 4, 8, 8, '{"maxRecordNum":10}', 2,
       '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE @page_id IS NOT NULL AND @layout_cnt = 0;

-- sort3 quick_nav（OA 默认入口；WB-T4）
INSERT INTO `system_home_page_layout`
    (`page_id`, `component_code`, `position_x`, `position_y`, `width`, `height`, `config`, `sort`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT @page_id, 'workbench_quick_nav', 0, 12, 24, 5,
       '{"title":"快捷入口","items":[{"title":"待办任务","url":"/bpm/task/todo","icon":"fa:slack","color":"#3fb27f"},{"title":"开票申请","url":"/finance/invoice-application","icon":"ep:document","color":"#409EFF"},{"title":"到款认领","url":"/finance/receipt-claim","icon":"fa:hand-o-up","color":"#e18525"},{"title":"业务订单","url":"/finance/business-order","icon":"fa:briefcase","color":"#7c3aed"},{"title":"银行流水","url":"/finance/receipt","icon":"fa:bank","color":"#1fdaca"}]}',
       3,
       '1', NOW(), '1', NOW(), b'0', @tenant_id
WHERE @page_id IS NOT NULL AND @layout_cnt = 0;

-- ---------- 3b. WB-T4：若已有 quick_nav 行且 items 为空/缺省，补 OA 默认入口（不覆盖非空 items）----------
UPDATE `system_home_page_layout`
SET `config` = '{"title":"快捷入口","items":[{"title":"待办任务","url":"/bpm/task/todo","icon":"fa:slack","color":"#3fb27f"},{"title":"开票申请","url":"/finance/invoice-application","icon":"ep:document","color":"#409EFF"},{"title":"到款认领","url":"/finance/receipt-claim","icon":"fa:hand-o-up","color":"#e18525"},{"title":"业务订单","url":"/finance/business-order","icon":"fa:briefcase","color":"#7c3aed"},{"title":"银行流水","url":"/finance/receipt","icon":"fa:bank","color":"#1fdaca"}]}',
    `updater` = '1',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `page_id` = @page_id
  AND `component_code` = 'workbench_quick_nav'
  AND (
        `config` IS NULL
     OR `config` = ''
     OR `config` = '{}'
     OR `config` LIKE '%"items":[]%'
     OR (`config` NOT LIKE '%"items"%')
  );

-- ---------- FORCE 重种（默认注释；仅空环境/明确回滚时手工解开）----------
-- DELETE FROM `system_home_page_layout` WHERE `page_id` = @page_id AND `deleted` = b'0';
-- 然后重新执行本节 INSERT（需临时把 @layout_cnt 条件去掉或设为 0）

-- 验收：
-- SELECT id, code, is_default, status FROM system_home_page WHERE code='default_workspace';
-- SELECT component_code, position_x, position_y, width, height, sort FROM system_home_page_layout WHERE page_id=@page_id AND deleted=0;
