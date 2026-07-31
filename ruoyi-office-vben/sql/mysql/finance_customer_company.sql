-- 财务客户公司（购方档案）+ 开票弱关联列（CC-T1）
-- 幂等：CREATE IF NOT EXISTS + information_schema 判列后加列

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_customer_company` (
    `id`            bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `code`          varchar(64)  NOT NULL COMMENT '客户公司编码（系统生成）',
    `name`          varchar(255) NOT NULL COMMENT '客户公司名称（购方名称）',
    `tax_no`        varchar(64)  NOT NULL COMMENT '纳税人识别号',
    `bank_name`     varchar(255) DEFAULT NULL COMMENT '开户银行',
    `bank_account`  varchar(128) DEFAULT NULL COMMENT '银行账号',
    `address`       varchar(512) DEFAULT NULL COMMENT '邮寄地址',
    `phone`         varchar(64)  DEFAULT NULL COMMENT '联系电话',
    `contact_name`  varchar(128) DEFAULT NULL COMMENT '联系人',
    `email`         varchar(255) DEFAULT NULL COMMENT '联系邮箱',
    `party_type`    varchar(32)  NOT NULL DEFAULT 'CUSTOMER' COMMENT '类型：C1 固定 CUSTOMER',
    `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '状态：0启用 1停用',
    `creator`       varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`       varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tax_no_tenant_deleted` (`tax_no`, `tenant_id`, `deleted`),
    UNIQUE KEY `uk_code_tenant_deleted` (`code`, `tenant_id`, `deleted`),
    KEY `idx_name` (`name`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='财务客户公司（购方档案）';

-- 开票申请：弱关联客户公司（历史可空）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'finance_invoice_application'
      AND COLUMN_NAME = 'customer_company_id'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE `finance_invoice_application` ADD COLUMN `customer_company_id` bigint DEFAULT NULL COMMENT ''弱关联客户公司；票面以 buyer_* 快照为准'' AFTER `buyer_bank_account`',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
