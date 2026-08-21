-- 最低工资基数（病假长假档）。幂等建表。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `hrm_min_wage` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `amount` decimal(10, 2) NOT NULL COMMENT '最低工资金额',
    `effective_month` int NOT NULL COMMENT '生效年月 YYYYMM',
    `creator` varchar(64) DEFAULT '',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` varchar(64) DEFAULT '',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` bit(1) NOT NULL DEFAULT b'0',
    `tenant_id` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_hrm_min_wage_month` (`tenant_id`, `effective_month`)
) COMMENT = '薪酬最低工资历史';
