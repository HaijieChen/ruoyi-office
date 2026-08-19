-- 费用报销：发票模式 / 前置单 / 行发票（幂等加列）
SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='finance_expense_reimbursement' AND COLUMN_NAME='invoice_mode'),
        'SELECT 1',
        'ALTER TABLE finance_expense_reimbursement ADD COLUMN invoice_mode varchar(32) NOT NULL DEFAULT \'WITH_INVOICE\' COMMENT \'WITH_INVOICE/NO_INVOICE\' AFTER proxy_ticket, ADD COLUMN process_key varchar(64) DEFAULT NULL COMMENT \'oa_expense_reimbursement / oa_expense_no_invoice\' AFTER invoice_mode'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='finance_expense_reimbursement_line' AND COLUMN_NAME='invoice_file_url'),
        'SELECT 1',
        'ALTER TABLE finance_expense_reimbursement_line ADD COLUMN invoice_file_url varchar(1024) DEFAULT NULL COMMENT \'invoice url\' AFTER attachments, ADD COLUMN predoc_type varchar(16) DEFAULT NULL COMMENT \'TRIP/OUTING\', ADD COLUMN predoc_process_instance_id varchar(64) DEFAULT NULL'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
