-- 合同签约归档资料（多 URL JSON）
SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='finance_contract_application' AND COLUMN_NAME='archive_file_urls'),
        'SELECT 1',
        'ALTER TABLE finance_contract_application ADD COLUMN archive_file_urls varchar(2048) DEFAULT NULL COMMENT ''归档资料 JSON URL 数组'' AFTER archived_at'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
