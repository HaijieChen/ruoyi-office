-- 银行到款：是否业务款 + 款项类型备注（幂等）
-- 规则：表单 business_fund 必填默认是；备注选填；导入空=否；仅展示不联动认领

SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_bank_receipt'
      AND column_name = 'business_fund');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_bank_receipt` ADD COLUMN `business_fund` bit(1) NOT NULL DEFAULT b''1'' COMMENT ''是否业务款'' AFTER `bank_serial_no`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_bank_receipt'
      AND column_name = 'fund_type_remark');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_bank_receipt` ADD COLUMN `fund_type_remark` varchar(255) DEFAULT NULL COMMENT ''款项类型备注'' AFTER `business_fund`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
