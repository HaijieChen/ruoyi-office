-- 月度薪酬批次表。幂等。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `hrm_payroll_batch` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `year_month` int NOT NULL COMMENT 'YYYYMM',
    `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hrm_payroll_batch_month` (`tenant_id`, `year_month`, `deleted`)
) COMMENT='月度工资批次';

CREATE TABLE IF NOT EXISTS `hrm_payroll_line` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `batch_id` bigint NOT NULL,
    `employee_id` bigint DEFAULT NULL,
    `employee_name` varchar(64) NOT NULL DEFAULT '',
    `punch_name` varchar(64) DEFAULT NULL,
    `payable` decimal(12, 2) DEFAULT NULL,
    `net` decimal(12, 2) DEFAULT NULL,
    `tax` decimal(12, 2) DEFAULT NULL,
    `overtime` decimal(12, 2) DEFAULT NULL,
    `id_card` varchar(32) DEFAULT NULL,
    `bank_account` varchar(64) DEFAULT NULL,
    `snapshot` bit(1) NOT NULL DEFAULT b'0',
    `user_id` bigint DEFAULT NULL,
    `sick_days` decimal(6, 2) DEFAULT NULL,
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_hrm_payroll_line_batch` (`batch_id`, `snapshot`)
) COMMENT='月度工资行（HR 行含证件卡号；员工快照行 snapshot=1 不含证件卡号）';
