-- 费用报销头表：其他附件（行程单等），逗号分隔 URL，最多 30 个
SET NAMES utf8mb4;

SET @sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE finance_expense_reimbursement ADD COLUMN extra_attachments TEXT NULL COMMENT ''其他附件 URL，逗号分隔，最多30个'' AFTER pay_voucher_url',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'finance_expense_reimbursement'
      AND COLUMN_NAME = 'extra_attachments'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
