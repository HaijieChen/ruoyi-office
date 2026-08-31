-- 费用报销明细：专票税额；发票类型补「其他」
SET NAMES utf8mb4;

SET @sql := (SELECT IF(EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'finance_expense_reimbursement_line'
      AND COLUMN_NAME = 'tax_amount'
), 'SELECT 1',
    'ALTER TABLE `finance_expense_reimbursement_line`
        ADD COLUMN `tax_amount` decimal(18,2) DEFAULT NULL COMMENT ''专票税额'' AFTER `amount`'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 3, '其他', '其他', 'finance_invoice_type', 0, 'default', '', '未能识别专票/普票', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_dict_data`
    WHERE `dict_type` = 'finance_invoice_type' AND `value` = '其他' AND `deleted` = b'0'
);
