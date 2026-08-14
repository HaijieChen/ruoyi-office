-- EXP-87 P1：付款支付明细（按实际转账笔次；账户快照不可变；合计=批准金额才办结）
-- 幂等 DDL

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `finance_payment_pay_line` (
    `id`                         bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `payment_application_id`     bigint NOT NULL COMMENT '付款申请 id',
    `company_bank_account_id`    bigint NOT NULL COMMENT '公司银行账户主数据 id',
    `entity_company_dept_id`     bigint NOT NULL COMMENT '主体公司 deptId（与账户归属一致）',
    `account_name_snapshot`      varchar(128)  NOT NULL COMMENT '账户名称快照',
    `bank_name_snapshot`         varchar(255)  NOT NULL COMMENT '开户行快照',
    `account_holder_snapshot`    varchar(255)  NOT NULL COMMENT '户名快照',
    `account_no_snapshot`        varchar(128)  NOT NULL COMMENT '账号完整快照（审计）',
    `account_no_masked_snapshot` varchar(64)   NOT NULL COMMENT '账号脱敏快照',
    `currency_snapshot`          varchar(16)   NOT NULL DEFAULT 'CNY' COMMENT '币种快照',
    `pay_amount`                 decimal(18,2) NOT NULL COMMENT '本笔支付金额',
    `actual_pay_date`            date NOT NULL COMMENT '实际支付日',
    `pay_voucher_url`            varchar(1024) NOT NULL COMMENT '支付凭证',
    `erp_voucher_no`             varchar(64)   DEFAULT NULL COMMENT 'ERP 凭证号',
    `idempotency_key`            varchar(64)   DEFAULT NULL COMMENT '客户端幂等键（可选）',
    `creator`                    varchar(64)   DEFAULT '' COMMENT '创建者',
    `create_time`                datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                    varchar(64)   DEFAULT '' COMMENT '更新者',
    `update_time`                datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                    bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`                  bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_fppl_app` (`payment_application_id`),
    KEY `idx_fppl_account` (`company_bank_account_id`),
    KEY `idx_fppl_entity` (`entity_company_dept_id`),
    UNIQUE KEY `uk_fppl_app_idem` (`payment_application_id`, `idempotency_key`, `tenant_id`, `deleted`)
) ENGINE=InnoDB COMMENT='付款支付明细（账户快照）';

-- 申请单头：业务类型 + 期间 + 收款方/部分字段放宽（薪资/税金无传统收款方）
SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
      AND column_name = 'application_kind');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_payment_application` ADD COLUMN `application_kind` varchar(32) NOT NULL DEFAULT ''ORDINARY'' COMMENT ''ORDINARY/SALARY/TAX'' AFTER `status`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
      AND column_name = 'period_label');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_payment_application` ADD COLUMN `period_label` varchar(64) DEFAULT NULL COMMENT ''薪资期间/税款所属期'' AFTER `apply_date`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 收款方等对薪资/税金可空（历史普通付款仍有值）
SET @sql = (
    SELECT IF(
        (SELECT IS_NULLABLE FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
           AND column_name = 'payee_company_id') = 'NO',
        'ALTER TABLE `finance_payment_application` MODIFY COLUMN `payee_company_id` bigint DEFAULT NULL COMMENT ''收款方客商 id（普通付款必填）''',
        'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (
    SELECT IF(
        (SELECT IS_NULLABLE FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
           AND column_name = 'payee_name') = 'NO',
        'ALTER TABLE `finance_payment_application` MODIFY COLUMN `payee_name` varchar(255) DEFAULT NULL COMMENT ''收款方名称快照''',
        'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (
    SELECT IF(
        (SELECT IS_NULLABLE FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
           AND column_name = 'payee_bank_name') = 'NO',
        'ALTER TABLE `finance_payment_application` MODIFY COLUMN `payee_bank_name` varchar(255) DEFAULT NULL COMMENT ''开户行快照''',
        'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (
    SELECT IF(
        (SELECT IS_NULLABLE FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
           AND column_name = 'payee_bank_account') = 'NO',
        'ALTER TABLE `finance_payment_application` MODIFY COLUMN `payee_bank_account` varchar(128) DEFAULT NULL COMMENT ''账号快照''',
        'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (
    SELECT IF(
        (SELECT IS_NULLABLE FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
           AND column_name = 'business_settlement_term') = 'NO',
        'ALTER TABLE `finance_payment_application` MODIFY COLUMN `business_settlement_term` varchar(255) DEFAULT NULL COMMENT ''业务结算账期（普通付款）''',
        'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (
    SELECT IF(
        (SELECT IS_NULLABLE FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
           AND column_name = 'pay_method') = 'NO',
        'ALTER TABLE `finance_payment_application` MODIFY COLUMN `pay_method` varchar(32) DEFAULT NULL COMMENT ''支付方式（普通付款）''',
        'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = (
    SELECT IF(
        (SELECT IS_NULLABLE FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
           AND column_name = 'cost_project') = 'NO',
        'ALTER TABLE `finance_payment_application` MODIFY COLUMN `cost_project` varchar(64) DEFAULT NULL COMMENT ''费用归属项目（普通付款）''',
        'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- payment_reason 对薪资/税金固定为 SALARY/TAX；历史保留
-- 普通新单服务端禁止 SALARY/TAX

CREATE TABLE IF NOT EXISTS `finance_payment_salary_line` (
    `id`                       bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `payment_application_id`   bigint NOT NULL COMMENT '付款申请 id',
    `entity_company_dept_id`   bigint NOT NULL COMMENT '主体公司 deptId',
    `entity_company_name`      varchar(100)  NOT NULL COMMENT '主体公司名称快照',
    `net_salary_amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '实发薪资',
    `personal_tax_amount`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '个税',
    `social_insurance_amount`  decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '社保（V1 汇总，含公积金口径由业务约定）',
    `currency`                 varchar(16)   NOT NULL DEFAULT 'CNY',
    `line_total`               decimal(18,2) NOT NULL COMMENT '行合计',
    `sort`                     int NOT NULL DEFAULT 0 COMMENT '排序',
    `creator`                  varchar(64)   DEFAULT '',
    `create_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                  varchar(64)   DEFAULT '',
    `update_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                  bit(1) NOT NULL DEFAULT b'0',
    `tenant_id`                bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_fpsl_app` (`payment_application_id`),
    KEY `idx_fpsl_entity` (`entity_company_dept_id`)
) ENGINE=InnoDB COMMENT='薪资付款多主体明细';

CREATE TABLE IF NOT EXISTS `finance_payment_tax_line` (
    `id`                       bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
    `payment_application_id`   bigint NOT NULL COMMENT '付款申请 id',
    `entity_company_dept_id`   bigint NOT NULL COMMENT '主体公司 deptId',
    `entity_company_name`      varchar(100)  NOT NULL COMMENT '主体公司名称快照',
    `vat_amount`               decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '增值税',
    `surcharge_amount`         decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '附加税',
    `stamp_tax_amount`         decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '印花税',
    `cit_amount`               decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '企业所得税',
    `currency`                 varchar(16)   NOT NULL DEFAULT 'CNY',
    `line_total`               decimal(18,2) NOT NULL COMMENT '行合计',
    `sort`                     int NOT NULL DEFAULT 0 COMMENT '排序',
    `creator`                  varchar(64)   DEFAULT '',
    `create_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater`                  varchar(64)   DEFAULT '',
    `update_time`              datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                  bit(1) NOT NULL DEFAULT b'0',
    `tenant_id`                bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_fptl_app` (`payment_application_id`),
    KEY `idx_fptl_entity` (`entity_company_dept_id`)
) ENGINE=InnoDB COMMENT='税金付款多主体明细';
