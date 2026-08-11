-- EXP-73 ORG-1：组织公司记账本位币（幂等）
-- functional_currency：仅 orgType=公司 时有意义；部门节点保持 NULL
-- 合法值：CNY / USD / HKD

SET NAMES utf8mb4;

-- 1) 列：system_dept.functional_currency
SET @col = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'system_dept'
      AND column_name = 'functional_currency');
SET @sql = IF(@col = 0,
    'ALTER TABLE `system_dept` ADD COLUMN `functional_currency` varchar(8) DEFAULT NULL COMMENT ''记账本位币 CNY/USD/HKD（仅公司）'' AFTER `org_type`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) 审计：启用公司尚未配置本位币（上线前必看；勿盲填全 CNY）
-- SELECT id, name, status, functional_currency
-- FROM system_dept
-- WHERE deleted = b'0' AND org_type = '1' AND status = 0
--   AND (functional_currency IS NULL OR functional_currency = '');

-- 3) 回填模板（业务提供映射后按公司改；示例占位，默认不执行）
-- 勿盲填全 CNY：映射未定时不得执行无条件 UPDATE。
-- UPDATE system_dept SET functional_currency = 'CNY', updater = 'exp73-backfill', update_time = NOW()
-- WHERE deleted = b'0' AND org_type = '1' AND id IN (/* 确认 CNY 的公司 id */);
-- UPDATE system_dept SET functional_currency = 'USD', updater = 'exp73-backfill', update_time = NOW()
-- WHERE deleted = b'0' AND org_type = '1' AND id IN (/* 确认 USD 的公司 id */);
-- UPDATE system_dept SET functional_currency = 'HKD', updater = 'exp73-backfill', update_time = NOW()
-- WHERE deleted = b'0' AND org_type = '1' AND id IN (/* 确认 HKD 的公司 id */);
