SET NAMES utf8mb4;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'business_subject'),
    'ALTER TABLE `finance_business_order` MODIFY COLUMN `business_subject` varchar(255) DEFAULT NULL COMMENT ''业务主体/客户''',
    'SELECT 1'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'order_date'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `order_date` date DEFAULT NULL COMMENT ''下单日期'' AFTER `remark`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'product_name'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `product_name` varchar(255) DEFAULT NULL COMMENT ''产品名称'' AFTER `order_date`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'contact_person'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `contact_person` varchar(128) DEFAULT NULL COMMENT ''对接人'' AFTER `product_name`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'execution_start_date'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `execution_start_date` date DEFAULT NULL COMMENT ''执行开始日'' AFTER `contact_person`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'execution_end_date'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `execution_end_date` date DEFAULT NULL COMMENT ''执行截止日'' AFTER `execution_start_date`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'payer_name'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `payer_name` varchar(255) DEFAULT NULL COMMENT ''付款方名称'' AFTER `execution_end_date`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'signed_execution_amount'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `signed_execution_amount` decimal(18,2) DEFAULT NULL COMMENT ''签单执行金额'' AFTER `payer_name`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'discount_rate'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `discount_rate` decimal(8,6) DEFAULT NULL COMMENT ''折扣率'' AFTER `signed_execution_amount`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'settlement_amount'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `settlement_amount` decimal(18,2) DEFAULT NULL COMMENT ''签单结算金额'' AFTER `discount_rate`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'bank_account'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `bank_account` varchar(128) DEFAULT NULL COMMENT ''银行账户'' AFTER `settlement_amount`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'source_row_hash'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `source_row_hash` char(64) DEFAULT NULL COMMENT ''导入源行哈希'' AFTER `bank_account`'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

SET @phase1d_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`statistics`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `index_name` = 'uk_source_row_hash_tenant_deleted'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD UNIQUE KEY `uk_source_row_hash_tenant_deleted` (`source_row_hash`, `tenant_id`, `deleted`)'
);
PREPARE phase1d_statement FROM @phase1d_sql;
EXECUTE phase1d_statement;
DEALLOCATE PREPARE phase1d_statement;

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`,
     `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '商务单导入', 'finance:business-order:import', 3, 5, page_menu.id, '', '', '', NULL,
       0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM (SELECT `id` FROM `system_menu`
      WHERE `deleted` = b'0' AND `component` = 'finance/business-order/index' LIMIT 1) page_menu
WHERE NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `deleted` = b'0' AND `permission` = 'finance:business-order:import'
);

UPDATE `system_menu`
SET `name` = '商务单导入',
    `sort` = 5,
    `parent_id` = (
        SELECT page_menu.id FROM (
            SELECT `id` FROM `system_menu`
            WHERE `deleted` = b'0' AND `component` = 'finance/business-order/index' LIMIT 1
        ) page_menu
    ),
    `update_time` = NOW()
WHERE `deleted` = b'0' AND `permission` = 'finance:business-order:import';
