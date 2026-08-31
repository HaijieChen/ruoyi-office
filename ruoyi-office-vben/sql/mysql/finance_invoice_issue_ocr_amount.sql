-- 办票附件：金额、发票号、开票日期（追加办票，不覆盖）
SET NAMES utf8mb4;

SET @add_amount = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'finance_invoice_application_file'
          AND column_name = 'amount'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_invoice_application_file` ADD COLUMN `amount` decimal(18,2) DEFAULT NULL COMMENT ''发票金额'' AFTER `file_name`'
);
PREPARE stmt FROM @add_amount;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_invoice_no = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'finance_invoice_application_file'
          AND column_name = 'invoice_no'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_invoice_application_file` ADD COLUMN `invoice_no` varchar(128) DEFAULT NULL COMMENT ''发票号'' AFTER `amount`'
);
PREPARE stmt FROM @add_invoice_no;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_invoice_date = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'finance_invoice_application_file'
          AND column_name = 'invoice_date'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_invoice_application_file` ADD COLUMN `invoice_date` date DEFAULT NULL COMMENT ''开票日期'' AFTER `invoice_no`'
);
PREPARE stmt FROM @add_invoice_date;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
