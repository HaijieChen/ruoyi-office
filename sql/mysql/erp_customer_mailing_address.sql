-- Add a mailing address to ERP customers. Safe to execute repeatedly.

SET @erp_customer_mailing_address_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'erp_customer'
      AND COLUMN_NAME = 'mailing_address'
);

SET @erp_customer_mailing_address_sql = IF(
    @erp_customer_mailing_address_exists = 0,
    'ALTER TABLE `erp_customer` ADD COLUMN `mailing_address` varchar(255) NULL COMMENT ''邮寄地址'' AFTER `bank_address`',
    'SELECT ''erp_customer.mailing_address already exists'' AS migration_status'
);

PREPARE erp_customer_mailing_address_stmt FROM @erp_customer_mailing_address_sql;
EXECUTE erp_customer_mailing_address_stmt;
DEALLOCATE PREPARE erp_customer_mailing_address_stmt;
