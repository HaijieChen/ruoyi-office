-- 费用报销明细发票号码（幂等）
SET NAMES utf8mb4;
SET @db := DATABASE();
SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=@db AND TABLE_NAME='finance_expense_reimbursement_line' AND COLUMN_NAME='invoice_no'),
    'SELECT 1',
    'ALTER TABLE finance_expense_reimbursement_line ADD COLUMN invoice_no varchar(32) DEFAULT NULL COMMENT ''发票号码'' AFTER invoice_file_url'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA=@db AND TABLE_NAME='finance_expense_reimbursement_line' AND INDEX_NAME='uk_expense_line_invoice_no'),
    'SELECT 1',
    'ALTER TABLE finance_expense_reimbursement_line ADD INDEX uk_expense_line_invoice_no (invoice_no)'
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
