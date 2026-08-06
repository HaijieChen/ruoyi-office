-- PAY-R1：付款资金链租户盘点 + 单租户迁移草案
-- 表：finance_payment_application / finance_customer_company / finance_contract_application
-- 实体已改为 TenantBaseDO；新 insert 写当前租户。历史 tenant_id=0 需盘点后迁移。
--
-- 【禁止】多租户生产环境未盘点前直接执行「全部回填 1」。
-- 单租户（仅 tenant_id=1 有效）可执行 SECTION B。

-- ========== SECTION A：盘点（只读）==========
SELECT 'finance_payment_application' AS tbl,
       SUM(CASE WHEN tenant_id = 0 OR tenant_id IS NULL THEN 1 ELSE 0 END) AS zero_or_null_cnt,
       COUNT(*) AS total_cnt
FROM finance_payment_application
WHERE deleted = b'0'
UNION ALL
SELECT 'finance_customer_company',
       SUM(CASE WHEN tenant_id = 0 OR tenant_id IS NULL THEN 1 ELSE 0 END),
       COUNT(*)
FROM finance_customer_company
WHERE deleted = b'0'
UNION ALL
SELECT 'finance_contract_application',
       SUM(CASE WHEN tenant_id = 0 OR tenant_id IS NULL THEN 1 ELSE 0 END),
       COUNT(*)
FROM finance_contract_application
WHERE deleted = b'0';

-- 按 tenant 聚合（回读用）
SELECT 'finance_payment_application' AS tbl, tenant_id, COUNT(*) AS cnt
FROM finance_payment_application WHERE deleted = b'0' GROUP BY tenant_id
UNION ALL
SELECT 'finance_customer_company', tenant_id, COUNT(*)
FROM finance_customer_company WHERE deleted = b'0' GROUP BY tenant_id
UNION ALL
SELECT 'finance_contract_application', tenant_id, COUNT(*)
FROM finance_contract_application WHERE deleted = b'0' GROUP BY tenant_id;

-- ========== SECTION B：单租户幂等回填（仅确认环境只有目标租户时执行）==========
-- 默认目标租户 = 1。多租户请改归属规则或手工映射后再 UPDATE。
-- SET @target_tenant := 1;
--
-- UPDATE finance_payment_application
-- SET tenant_id = @target_tenant, update_time = NOW()
-- WHERE deleted = b'0' AND (tenant_id = 0 OR tenant_id IS NULL);
--
-- UPDATE finance_customer_company
-- SET tenant_id = @target_tenant, update_time = NOW()
-- WHERE deleted = b'0' AND (tenant_id = 0 OR tenant_id IS NULL);
--
-- UPDATE finance_contract_application
-- SET tenant_id = @target_tenant, update_time = NOW()
-- WHERE deleted = b'0' AND (tenant_id = 0 OR tenant_id IS NULL);
--
-- -- PAY-R9：回读三表 zero_or_null_cnt 均应为 0
-- SELECT 'finance_payment_application' AS tbl,
--        SUM(CASE WHEN tenant_id = 0 OR tenant_id IS NULL THEN 1 ELSE 0 END) AS zero_or_null_cnt,
--        COUNT(*) AS total_cnt
-- FROM finance_payment_application WHERE deleted = b'0'
-- UNION ALL
-- SELECT 'finance_customer_company',
--        SUM(CASE WHEN tenant_id = 0 OR tenant_id IS NULL THEN 1 ELSE 0 END),
--        COUNT(*)
-- FROM finance_customer_company WHERE deleted = b'0'
-- UNION ALL
-- SELECT 'finance_contract_application',
--        SUM(CASE WHEN tenant_id = 0 OR tenant_id IS NULL THEN 1 ELSE 0 END),
--        COUNT(*)
-- FROM finance_contract_application WHERE deleted = b'0';
