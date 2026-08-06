-- PAY-P0-1：客商双角色 is_customer / is_supplier（可叠加）
-- 幂等：information_schema 判列后加列；历史回填仅客户
-- 参考：tech-plan-payment-approval TP1–TP3

SET NAMES utf8mb4;

-- is_customer
SET @col_is_customer := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'finance_customer_company'
      AND COLUMN_NAME = 'is_customer'
);
SET @sql_is_customer := IF(@col_is_customer = 0,
    'ALTER TABLE `finance_customer_company` ADD COLUMN `is_customer` tinyint NOT NULL DEFAULT 1 COMMENT ''是否客户角色 0/1'' AFTER `party_type`',
    'SELECT 1');
PREPARE stmt FROM @sql_is_customer;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- is_supplier
SET @col_is_supplier := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'finance_customer_company'
      AND COLUMN_NAME = 'is_supplier'
);
SET @sql_is_supplier := IF(@col_is_supplier = 0,
    'ALTER TABLE `finance_customer_company` ADD COLUMN `is_supplier` tinyint NOT NULL DEFAULT 0 COMMENT ''是否供应商角色 0/1'' AFTER `is_customer`',
    'SELECT 1');
PREPARE stmt FROM @sql_is_supplier;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史行：仅客户（兼容开票/合同）
UPDATE `finance_customer_company`
SET `is_customer` = 1,
    `is_supplier` = 0
WHERE `deleted` = b'0'
  AND (`is_customer` IS NULL OR (`is_customer` = 0 AND `is_supplier` = 0));

-- 可选索引（幂等：名已存在则跳过）
SET @idx_c := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'finance_customer_company'
      AND INDEX_NAME = 'idx_is_customer'
);
SET @sql_idx_c := IF(@idx_c = 0,
    'ALTER TABLE `finance_customer_company` ADD KEY `idx_is_customer` (`is_customer`)',
    'SELECT 1');
PREPARE stmt FROM @sql_idx_c;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_s := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'finance_customer_company'
      AND INDEX_NAME = 'idx_is_supplier'
);
SET @sql_idx_s := IF(@idx_s = 0,
    'ALTER TABLE `finance_customer_company` ADD KEY `idx_is_supplier` (`is_supplier`)',
    'SELECT 1');
PREPARE stmt FROM @sql_idx_s;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
