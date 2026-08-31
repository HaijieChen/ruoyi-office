-- 开票明细：商务单可空（非品牌商务挂销售合同）、行备注
SET NAMES utf8mb4;

SET @nullable_bo = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'finance_invoice_application_line'
          AND column_name = 'business_order_id'
          AND is_nullable = 'NO'
    ),
    'ALTER TABLE `finance_invoice_application_line` MODIFY COLUMN `business_order_id` bigint DEFAULT NULL COMMENT ''商务单编号（品牌商务必填）''',
    'SELECT 1'
);
PREPARE stmt FROM @nullable_bo;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_remark = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'finance_invoice_application_line'
          AND column_name = 'remark'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_invoice_application_line` ADD COLUMN `remark` varchar(500) DEFAULT NULL COMMENT ''明细备注'' AFTER `billing_period`'
);
PREPARE stmt FROM @add_remark;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
