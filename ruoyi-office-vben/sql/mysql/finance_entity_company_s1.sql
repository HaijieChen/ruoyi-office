-- S1：到款/签单主体公司 + 签单物理删除 bank_account（幂等）
-- 主体 = 组织架构公司（deptId + 名称快照）

-- 1) 银行到款：主体公司
SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_bank_receipt'
      AND column_name = 'entity_company_dept_id');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_bank_receipt` ADD COLUMN `entity_company_dept_id` bigint DEFAULT NULL COMMENT ''主体公司组织部门编号'' AFTER `bank_account`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_bank_receipt'
      AND column_name = 'entity_company_name');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_bank_receipt` ADD COLUMN `entity_company_name` varchar(100) DEFAULT NULL COMMENT ''主体公司名称快照'' AFTER `entity_company_dept_id`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) 商务签单：主体公司
SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_business_order'
      AND column_name = 'entity_company_dept_id');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_business_order` ADD COLUMN `entity_company_dept_id` bigint DEFAULT NULL COMMENT ''主体公司组织部门编号（我方签约主体）'' AFTER `order_no`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_business_order'
      AND column_name = 'entity_company_name');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_business_order` ADD COLUMN `entity_company_name` varchar(100) DEFAULT NULL COMMENT ''主体公司名称快照'' AFTER `entity_company_dept_id`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3) 签单：DROP 索引再 DROP bank_account
SET @idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'finance_business_order'
      AND index_name = 'idx_bank_account_order_date');
SET @sql = IF(@idx > 0,
    'ALTER TABLE `finance_business_order` DROP INDEX `idx_bank_account_order_date`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_business_order'
      AND column_name = 'bank_account');
SET @sql = IF(@col = 1,
    'ALTER TABLE `finance_business_order` DROP COLUMN `bank_account`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
