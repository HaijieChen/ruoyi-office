-- 差旅住宿城市档与超标原因（幂等）
SET NAMES utf8mb4;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'finance_expense_reimbursement_line'
        AND COLUMN_NAME = 'stay_city_tier') = 0,
    'ALTER TABLE finance_expense_reimbursement_line ADD COLUMN stay_city_tier VARCHAR(16) NULL COMMENT ''T1北上广深/OTHER其他'' AFTER remark',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'finance_expense_reimbursement_line'
        AND COLUMN_NAME = 'over_limit_reason') = 0,
    'ALTER TABLE finance_expense_reimbursement_line ADD COLUMN over_limit_reason VARCHAR(500) NULL COMMENT ''住宿超标原因'' AFTER stay_city_tier',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
