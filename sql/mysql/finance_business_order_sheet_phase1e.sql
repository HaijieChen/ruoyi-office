SET NAMES utf8mb4;

-- REQUIRED OPERATOR STEP: create and verify this logical backup before running any statement below.
-- docker exec ruoyi-office-mysql sh -lc 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" ruoyi-office finance_business_order finance_receipt_claim finance_receipt_claim_item finance_bank_receipt > /tmp/finance-business-signing-pre-phase1e.sql'
-- The migration intentionally does not create its own backup. Rollback requires restoring that verified dump.

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'import_date'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `import_date` date DEFAULT NULL COMMENT ''导入日期'' AFTER `order_no`'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'importer_id'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `importer_id` bigint DEFAULT NULL COMMENT ''导入人编号'' AFTER `import_date`'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'contract_process_id'),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `contract_process_id` varchar(127) DEFAULT NULL COMMENT ''合同审批流程编号'' AFTER `bank_account`'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'receivable_amount'),
    'UPDATE `finance_business_order` SET `settlement_amount` = `receivable_amount` WHERE `settlement_amount` IS NULL',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

DROP PROCEDURE IF EXISTS `finance_business_order_phase1e_guard`;
DELIMITER //
CREATE PROCEDURE `finance_business_order_phase1e_guard`()
BEGIN
    DECLARE unsafe_claim_balance_count bigint DEFAULT 0;

    SELECT COUNT(*) INTO unsafe_claim_balance_count
    FROM `finance_business_order`
    WHERE `confirmed_claimed_amount` > `settlement_amount`;

    IF unsafe_claim_balance_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'phase 1E refused: confirmed claims exceed settlement';
    END IF;
END//
DELIMITER ;
CALL `finance_business_order_phase1e_guard`();
DROP PROCEDURE `finance_business_order_phase1e_guard`;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'business_subject'),
    'ALTER TABLE `finance_business_order` DROP COLUMN `business_subject`',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'business_type'),
    'ALTER TABLE `finance_business_order` DROP COLUMN `business_type`',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'contract_ref'),
    'ALTER TABLE `finance_business_order` DROP COLUMN `contract_ref`',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'project_ref'),
    'ALTER TABLE `finance_business_order` DROP COLUMN `project_ref`',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'receivable_amount'),
    'ALTER TABLE `finance_business_order` DROP COLUMN `receivable_amount`',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'payable_amount'),
    'ALTER TABLE `finance_business_order` DROP COLUMN `payable_amount`',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'owner_id'),
    'ALTER TABLE `finance_business_order` DROP COLUMN `owner_id`',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;

SET @phase1e_sql = IF(
    EXISTS (SELECT 1 FROM `information_schema`.`columns`
            WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
              AND `column_name` = 'status'),
    'ALTER TABLE `finance_business_order` DROP COLUMN `status`',
    'SELECT 1'
);
PREPARE phase1e_statement FROM @phase1e_sql;
EXECUTE phase1e_statement;
DEALLOCATE PREPARE phase1e_statement;
