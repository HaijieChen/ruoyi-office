SET @currency_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'finance_business_order'
      AND column_name = 'currency'
);

SET @remove_currency_sql = IF(
    @currency_column_exists = 1,
    'ALTER TABLE `finance_business_order` DROP COLUMN `currency`',
    'SELECT 1'
);

PREPARE remove_currency_statement FROM @remove_currency_sql;
EXECUTE remove_currency_statement;
DEALLOCATE PREPARE remove_currency_statement;
