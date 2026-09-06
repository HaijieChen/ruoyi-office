-- Disposable U9 fixture only (oa_u9_iso). Do not run against 33061.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `bpm_oa_overtime` (
    `id`                     bigint NOT NULL AUTO_INCREMENT,
    `user_id`                bigint NOT NULL,
    `reason`                 varchar(500) NOT NULL,
    `start_time`             datetime NOT NULL,
    `end_time`               datetime NOT NULL,
    `hours`                  decimal(8,1) NOT NULL,
    `holiday`                varchar(16) NOT NULL,
    `attachment_urls`        json DEFAULT NULL,
    `status`                 tinyint NOT NULL,
    `process_instance_id`    varchar(64) DEFAULT NULL,
    `attendance_sync_status` varchar(32) NOT NULL DEFAULT 'NOT_SYNCED',
    `creator`                varchar(64) DEFAULT '',
    `create_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                varchar(64) DEFAULT '',
    `update_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                bit(1) NOT NULL DEFAULT b'0',
    `tenant_id`              bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_start` (`user_id`, `start_time`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `bpm_oa_punch_correction` (
    `id`                     bigint NOT NULL AUTO_INCREMENT,
    `user_id`                bigint NOT NULL,
    `punch_date`             date NOT NULL,
    `punch_time`             datetime NOT NULL,
    `reason`                 varchar(500) NOT NULL,
    `attachment_urls`        json DEFAULT NULL,
    `status`                 tinyint NOT NULL,
    `process_instance_id`    varchar(64) DEFAULT NULL,
    `attendance_sync_status` varchar(32) NOT NULL DEFAULT 'NOT_SYNCED',
    `creator`                varchar(64) DEFAULT '',
    `create_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                varchar(64) DEFAULT '',
    `update_time`            datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                bit(1) NOT NULL DEFAULT b'0',
    `tenant_id`              bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_punch_date` (`user_id`, `punch_date`)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS `bpm_oa_quota_lock` (
    `tenant_id`   bigint NOT NULL DEFAULT 0,
    `user_id`     bigint NOT NULL,
    `quota_type`  varchar(32) NOT NULL,
    `period`      varchar(16) NOT NULL,
    `creator`     varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`     varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`tenant_id`, `user_id`, `quota_type`, `period`)
) ENGINE=InnoDB;
