-- 开票红冲 U1：原单锁/已红冲列 + 红冲台账（幂等）
SET NAMES utf8mb4;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'finance_invoice_application'
                 AND COLUMN_NAME = 'red_flushed'),
        'SELECT 1',
        'ALTER TABLE `finance_invoice_application`
            ADD COLUMN `red_flushed` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否已红冲'' AFTER `voided`,
            ADD COLUMN `red_flush_lock_application_id` bigint DEFAULT NULL COMMENT ''进行中的红冲申请 id'' AFTER `red_flushed`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `finance_invoice_redflush` (
    `id`                          bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `application_no`              varchar(64) NOT NULL COMMENT '红冲申请单号',
    `process_instance_id`         varchar(64) DEFAULT NULL COMMENT 'BPM 流程实例',
    `predecessor_application_id`  bigint NOT NULL COMMENT '前置开票申请 id',
    `approval_status`             varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态',
    `issue_status`                tinyint NOT NULL DEFAULT 0 COMMENT '办票状态 0/2',
    `reason`                      varchar(500) NOT NULL COMMENT '红冲原因',
    `special_note`                varchar(500) DEFAULT NULL COMMENT '特殊情况说明',
    `total_amount`                decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '价税合计快照',
    `currency`                    varchar(8) DEFAULT NULL COMMENT '币种快照',
    `applicant_user_id`           bigint NOT NULL COMMENT '申请人',
    `voided`                      bit(1) NOT NULL DEFAULT b'0' COMMENT '是否作废',
    `creator`                     varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`                 datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                     varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`                 datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                     bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                   bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_redflush_no_tenant_deleted` (`application_no`, `tenant_id`, `deleted`),
    KEY `idx_predecessor` (`predecessor_application_id`),
    KEY `idx_process_instance` (`process_instance_id`)
) ENGINE=InnoDB COMMENT='财务开票红冲申请';
