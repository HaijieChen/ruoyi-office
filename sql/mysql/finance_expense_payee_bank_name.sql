-- 费用报销头：开户行快照（STORY-0095 U1）
-- 幂等加列；旧行 DEFAULT ''
SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='finance_expense_reimbursement' AND COLUMN_NAME='payee_bank_name'),
        'SELECT 1',
        'ALTER TABLE finance_expense_reimbursement ADD COLUMN payee_bank_name varchar(200) NOT NULL DEFAULT \'\' COMMENT \'开户行快照（提交时）\' AFTER payee_account_name'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
