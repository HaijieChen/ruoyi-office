-- 审计表补充操作人姓名（快照），并回填历史数据
-- 幂等：列不存在才 ADD；可用 system_users.nickname 回填

-- 1) 银行到款生命周期审计：operator_name
SET @add_lifecycle_operator_name = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = 'finance_receipt_lifecycle_audit'
          AND `column_name` = 'operator_name'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_lifecycle_audit`
        ADD COLUMN `operator_name` varchar(64) DEFAULT NULL COMMENT ''操作人姓名'' AFTER `operator_id`'
);
PREPARE stmt_lifecycle_operator_name FROM @add_lifecycle_operator_name;
EXECUTE stmt_lifecycle_operator_name;
DEALLOCATE PREPARE stmt_lifecycle_operator_name;

-- 2) 认领撤销审计：reviewer_name
SET @add_revoke_reviewer_name = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = 'finance_receipt_claim_revoke_audit'
          AND `column_name` = 'reviewer_name'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_receipt_claim_revoke_audit`
        ADD COLUMN `reviewer_name` varchar(64) DEFAULT NULL COMMENT ''撤销操作人姓名'' AFTER `reviewer_id`'
);
PREPARE stmt_revoke_reviewer_name FROM @add_revoke_reviewer_name;
EXECUTE stmt_revoke_reviewer_name;
DEALLOCATE PREPARE stmt_revoke_reviewer_name;

-- 3) 历史数据回填（仅填空）
UPDATE `finance_receipt_lifecycle_audit` a
    LEFT JOIN `system_users` u ON u.`id` = a.`operator_id` AND u.`deleted` = b'0'
SET a.`operator_name` = u.`nickname`
WHERE (a.`operator_name` IS NULL OR a.`operator_name` = '')
  AND u.`nickname` IS NOT NULL
  AND u.`nickname` <> '';

UPDATE `finance_receipt_claim_revoke_audit` a
    LEFT JOIN `system_users` u ON u.`id` = a.`reviewer_id` AND u.`deleted` = b'0'
SET a.`reviewer_name` = u.`nickname`
WHERE (a.`reviewer_name` IS NULL OR a.`reviewer_name` = '')
  AND u.`nickname` IS NOT NULL
  AND u.`nickname` <> '';
