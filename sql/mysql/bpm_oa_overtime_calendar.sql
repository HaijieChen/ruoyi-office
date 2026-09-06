-- 加班节假日日历版本（幂等）+ 2026 已核验 ACTIVE 种子 + 菜单 + 站内信 + 定时任务

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `bpm_oa_overtime_calendar_version` (
    `id`                    bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `calendar_year`         int NOT NULL COMMENT '日历年份',
    `source`                varchar(128) DEFAULT NULL COMMENT '来源文号',
    `source_url`            varchar(512) DEFAULT NULL COMMENT '来源 URL',
    `fetched_at`            datetime DEFAULT NULL COMMENT '抓取时间',
    `content_hash`          varchar(64) NOT NULL COMMENT '内容指纹',
    `status`                varchar(16) NOT NULL COMMENT 'ACTIVE/PENDING/REJECTED/FAILED/NOT_PUBLISHED',
    `verified_by`           bigint DEFAULT NULL COMMENT '核验人',
    `verified_at`           datetime DEFAULT NULL COMMENT '核验时间',
    `raw_excerpt`           mediumtext COMMENT '原始摘录',
    `parse_note`            varchar(1000) DEFAULT NULL COMMENT '解析说明',
    `diff_json`             json DEFAULT NULL COMMENT '与上一生效版差异',
    `notify_fingerprint`    varchar(80) DEFAULT NULL COMMENT '通知去重',
    `legal_holidays_json`   json DEFAULT NULL COMMENT '法定节假日',
    `makeup_workdays_json`  json DEFAULT NULL COMMENT '调休上班',
    `makeup_rest_days_json` json DEFAULT NULL COMMENT '调休休息日',
    `weekends_json`         json DEFAULT NULL COMMENT '普通周末',
    `creator`               varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`               varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`           datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_year_hash` (`calendar_year`, `content_hash`, `deleted`),
    KEY `idx_year_status` (`calendar_year`, `status`)
) ENGINE=InnoDB COMMENT='OA 加班节假日日历版本';

INSERT INTO `bpm_oa_overtime_calendar_version` (
    `calendar_year`, `source`, `source_url`, `fetched_at`, `content_hash`, `status`,
    `verified_by`, `verified_at`, `parse_note`, `legal_holidays_json`, `makeup_workdays_json`,
    `makeup_rest_days_json`, `creator`, `updater`, `deleted`)
SELECT 2026, '国办发明电〔2025〕7号',
       'https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm',
       NOW(), 'seed-2026-legal13-labor2', 'ACTIVE', 1, NOW(),
       '国令第795号：劳动节2天（5月1、2日）。2026-05-03为普通周末。',
       '["2026-01-01","2026-02-16","2026-02-17","2026-02-18","2026-02-19","2026-04-04","2026-05-01","2026-05-02","2026-06-19","2026-09-25","2026-10-01","2026-10-02","2026-10-03"]',
       '["2026-01-04","2026-02-14","2026-02-28","2026-05-09","2026-09-20","2026-10-10"]',
       '["2026-01-02","2026-02-20","2026-02-23","2026-04-06","2026-05-04","2026-05-05","2026-10-05","2026-10-06","2026-10-07"]',
       'admin', 'admin', b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `bpm_oa_overtime_calendar_version`
    WHERE `calendar_year` = 2026 AND `content_hash` = 'seed-2026-legal13-labor2' AND `deleted` = b'0'
);

INSERT INTO `system_notify_template` (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `deleted`)
SELECT '加班节假日日历', 'bpm_oa_overtime_calendar', '系统',
       '加班节假日日历 {year}：{event}（{status}）。请到「节假日日历」核验，通过后才会生效。',
       2, '["year","event","status"]', 0, '抓取失败/待核验/未发布', 'admin', b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `system_notify_template` WHERE `code` = 'bpm_oa_overtime_calendar' AND `deleted` = b'0'
);

INSERT INTO `infra_job` (`name`, `status`, `handler_name`, `handler_param`, `cron_expression`,
                         `retry_count`, `retry_interval`, `monitor_timeout`, `creator`, `deleted`)
SELECT '加班节假日日历抓取', 1, 'oaOvertimeCalendarFetchJob', '', '0 0 9 ? * MON',
       1, 5000, 0, 'admin', b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM `infra_job` WHERE `handler_name` = 'oaOvertimeCalendarFetchJob' AND `deleted` = b'0'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '节假日日历', '', 2, 9,
       (SELECT `parent_id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/oa/overtime/index' LIMIT 1),
       'overtime-calendar', 'ep:calendar', 'bpm/oa/overtime-calendar/index', 'BpmOAOvertimeCalendar',
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE (SELECT `parent_id` FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/oa/overtime/index' LIMIT 1) IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu` WHERE `deleted` = b'0' AND `component` = 'bpm/oa/overtime-calendar/index'
);

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT btn.name, btn.permission, 3, btn.sort, parent.id, '', '', NULL, NULL,
       0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
    SELECT '日历查询' AS name, 'bpm:oa-overtime-calendar:query' AS permission, 1 AS sort
    UNION ALL SELECT '日历核验启用', 'bpm:oa-overtime-calendar:verify', 2
) btn
CROSS JOIN (
    SELECT `id` FROM `system_menu`
    WHERE `deleted` = b'0' AND `component` = 'bpm/oa/overtime-calendar/index' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu` m WHERE m.`deleted` = b'0' AND m.`permission` = btn.permission
);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', NOW(), 'admin', NOW(), b'0', 1
FROM `system_role` r
CROSS JOIN `system_menu` m
WHERE r.`deleted` = b'0' AND r.`code` IN ('hr_admin', 'super_admin') AND r.`tenant_id` = 1
  AND m.`deleted` = b'0'
  AND (m.`component` = 'bpm/oa/overtime-calendar/index'
       OR m.`permission` IN ('bpm:oa-overtime-calendar:query', 'bpm:oa-overtime-calendar:verify'))
  AND NOT EXISTS (
      SELECT 1 FROM `system_role_menu` rm
      WHERE rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id` AND rm.`tenant_id` = 1 AND rm.`deleted` = b'0'
  );
