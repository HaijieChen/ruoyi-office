-- OA 出差 / 外出申请表 + 出差类型字典（幂等可重复执行）

SET NAMES utf8mb4;

-- ----------------------------
-- Table: bpm_oa_business_trip
-- ----------------------------
CREATE TABLE IF NOT EXISTS `bpm_oa_business_trip` (
    `id`                     bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`                bigint NOT NULL COMMENT '申请人的用户编号',
    `type`                   tinyint NOT NULL COMMENT '出差类型：1市内 2省内 3省外 4国外',
    `start_time`             datetime NOT NULL COMMENT '开始时间',
    `end_time`               datetime NOT NULL COMMENT '结束时间',
    `hours`                  decimal(8,1) NOT NULL COMMENT '时长（小时）',
    `status`                 tinyint NOT NULL COMMENT '审批结果',
    `process_instance_id`    varchar(64) DEFAULT NULL COMMENT '流程实例的编号',
    `attendance_sync_status` varchar(32) NOT NULL DEFAULT 'NOT_SYNCED' COMMENT '考勤同步状态：NOT_SYNCED/SYNCED/FAILED',
    `creator`                varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='OA 出差申请';

-- ----------------------------
-- Table: bpm_oa_outing
-- ----------------------------
CREATE TABLE IF NOT EXISTS `bpm_oa_outing` (
    `id`                     bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `user_id`                bigint NOT NULL COMMENT '申请人的用户编号',
    `reason`                 varchar(500) NOT NULL COMMENT '外出事由',
    `location`               varchar(200) NOT NULL COMMENT '外出地点',
    `start_time`             datetime NOT NULL COMMENT '开始时间',
    `end_time`               datetime NOT NULL COMMENT '结束时间',
    `hours`                  decimal(8,1) NOT NULL COMMENT '时长（小时）',
    `need_output`            varchar(16) DEFAULT NULL COMMENT '是否需产出（infra_boolean_string）',
    `attachment_urls`        json DEFAULT NULL COMMENT '附件 URL 数组',
    `status`                 tinyint NOT NULL COMMENT '审批结果',
    `process_instance_id`    varchar(64) DEFAULT NULL COMMENT '流程实例的编号',
    `attendance_sync_status` varchar(32) NOT NULL DEFAULT 'NOT_SYNCED' COMMENT '考勤同步状态：NOT_SYNCED/SYNCED/FAILED',
    `creator`                varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`              bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='OA 外出申请';

-- ----------------------------
-- Dict: bpm_oa_trip_type
-- ----------------------------
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'OA 出差类型', 'bpm_oa_trip_type', 0, '出差类型：市内/省内/省外/国外', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_type` WHERE `type` = 'bpm_oa_trip_type' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 1, '市内', '1', 'bpm_oa_trip_type', 0, 'primary', '', '市内出差', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_type' AND `value` = '1' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 2, '省内', '2', 'bpm_oa_trip_type', 0, 'success', '', '省内出差', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_type' AND `value` = '2' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '省外', '3', 'bpm_oa_trip_type', 0, 'warning', '', '省外出差', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_type' AND `value` = '3' AND `deleted` = b'0'
);

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 4, '国外', '4', 'bpm_oa_trip_type', 0, 'danger', '', '国外出差', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'bpm_oa_trip_type' AND `value` = '4' AND `deleted` = b'0'
);
