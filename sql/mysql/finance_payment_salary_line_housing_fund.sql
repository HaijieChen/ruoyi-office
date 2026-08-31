-- 薪资付款明细：可选公积金（不并进社保）
-- 幂等加列；旧行 DEFAULT 0.00，不改历史合计
SET NAMES utf8mb4;
SET @db := DATABASE();

SET @sql := (
    SELECT IF(
        EXISTS(SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA=@db AND TABLE_NAME='finance_payment_salary_line' AND COLUMN_NAME='housing_fund_amount'),
        'SELECT 1',
        'ALTER TABLE finance_payment_salary_line ADD COLUMN housing_fund_amount decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT ''公积金'' AFTER social_insurance_amount'
    )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
