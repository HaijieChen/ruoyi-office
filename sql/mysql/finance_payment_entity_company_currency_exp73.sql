-- EXP-73 PAY-1：付款申请主体公司 + 币种契约强化（幂等）
-- 历史主体：nullable，不猜测回填；历史币种保持已有值
-- 新单/驳回重提：服务端强制主体 + CNY/USD/HKD

SET NAMES utf8mb4;

-- 1) 主体公司组织 ID
SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
      AND column_name = 'entity_company_dept_id');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_payment_application` ADD COLUMN `entity_company_dept_id` bigint DEFAULT NULL COMMENT ''主体公司组织部门编号'' AFTER `applicant_dept_id`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) 主体公司名称快照（仅服务端写入）
SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
      AND column_name = 'entity_company_name');
SET @sql = IF(@col = 0,
    'ALTER TABLE `finance_payment_application` ADD COLUMN `entity_company_name` varchar(100) DEFAULT NULL COMMENT ''主体公司名称快照'' AFTER `entity_company_dept_id`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3) 索引（可选筛选）
SET @idx = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'finance_payment_application'
      AND index_name = 'idx_pay_entity_company');
SET @sql = IF(@idx = 0,
    'ALTER TABLE `finance_payment_application` ADD KEY `idx_pay_entity_company` (`entity_company_dept_id`)',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 4) 审计：历史无主体
-- SELECT COUNT(*) AS hist_no_entity FROM finance_payment_application
-- WHERE deleted = b'0' AND entity_company_dept_id IS NULL;
