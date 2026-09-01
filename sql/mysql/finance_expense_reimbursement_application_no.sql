-- 费用报销申请单号（待办单据编号）
-- 幂等加列
SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='finance_expense_reimbursement' AND COLUMN_NAME='application_no'),
        'SELECT 1',
        'ALTER TABLE finance_expense_reimbursement ADD COLUMN application_no varchar(32) DEFAULT NULL COMMENT ''申请单号 EXP-YYYYMMDD-n'' AFTER id'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
