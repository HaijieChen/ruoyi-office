-- EXP-73 P2：合同/签单/开票/到款 主体公司 + 交易币种（幂等）
-- 历史：主体/币种列可空，不猜测回填；新单服务端强校验
-- 关联：同币种约束由应用层强制，不做汇率

SET NAMES utf8mb4;

-- ========== 1. 合同：entity_company + currency ==========
SET @col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_contract_application'
      AND column_name = 'entity_company_dept_id');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_contract_application` ADD COLUMN `entity_company_dept_id` bigint DEFAULT NULL COMMENT ''签约主体组织部门编号'' AFTER `sign_company`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_contract_application'
      AND column_name = 'entity_company_name');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_contract_application` ADD COLUMN `entity_company_name` varchar(100) DEFAULT NULL COMMENT ''签约主体名称快照'' AFTER `entity_company_dept_id`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_contract_application'
      AND column_name = 'currency');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_contract_application` ADD COLUMN `currency` varchar(16) DEFAULT NULL COMMENT ''合同币种 CNY/USD/HKD'' AFTER `contract_amount`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'finance_contract_application'
      AND index_name = 'idx_contract_entity_company');
SET @sql = IF(@idx = 0,
    'ALTER TABLE `finance_contract_application` ADD KEY `idx_contract_entity_company` (`entity_company_dept_id`)',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ========== 2. 商务签单：currency（主体已有） ==========
SET @col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_business_order'
      AND column_name = 'currency');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_business_order` ADD COLUMN `currency` varchar(16) DEFAULT NULL COMMENT ''交易币种 CNY/USD/HKD'' AFTER `settlement_amount`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ========== 3. 开票：currency（开票公司 ID+快照已有） ==========
SET @col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_invoice_application'
      AND column_name = 'currency');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_invoice_application` ADD COLUMN `currency` varchar(16) DEFAULT NULL COMMENT ''开票币种 CNY/USD/HKD'' AFTER `total_amount`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ========== 4. 银行到款：currency（主体已有） ==========
SET @col = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_bank_receipt'
      AND column_name = 'currency');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_bank_receipt` ADD COLUMN `currency` varchar(16) DEFAULT NULL COMMENT ''到款币种 CNY/USD/HKD'' AFTER `transaction_amount`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 审计（可选）
-- SELECT 'contract_no_entity' t, COUNT(*) c FROM finance_contract_application WHERE deleted=b'0' AND entity_company_dept_id IS NULL;
-- SELECT 'bo_no_currency' t, COUNT(*) c FROM finance_business_order WHERE deleted=b'0' AND (currency IS NULL OR currency='');
