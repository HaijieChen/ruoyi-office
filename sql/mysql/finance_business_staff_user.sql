SET NAMES utf8mb4;

SET @db := DATABASE();

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'finance_contract_application' AND COLUMN_NAME = 'business_staff_user_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `finance_contract_application` ADD COLUMN `business_staff_user_id` bigint NULL COMMENT ''业务人员用户编号'' AFTER `applicant_user_id`',
  'SELECT ''skip contract business_staff_user_id'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'finance_invoice_application' AND COLUMN_NAME = 'business_staff_user_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `finance_invoice_application` ADD COLUMN `business_staff_user_id` bigint NULL COMMENT ''业务人员用户编号'' AFTER `applicant_user_id`',
  'SELECT ''skip invoice business_staff_user_id'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'finance_payment_application' AND COLUMN_NAME = 'business_staff_user_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `finance_payment_application` ADD COLUMN `business_staff_user_id` bigint NULL COMMENT ''业务人员用户编号'' AFTER `applicant_user_id`',
  'SELECT ''skip payment business_staff_user_id'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'finance_business_order' AND COLUMN_NAME = 'business_staff_user_id');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE `finance_business_order` ADD COLUMN `business_staff_user_id` bigint NULL COMMENT ''业务人员用户编号'' AFTER `importer_id`',
  'SELECT ''skip business_order business_staff_user_id'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
