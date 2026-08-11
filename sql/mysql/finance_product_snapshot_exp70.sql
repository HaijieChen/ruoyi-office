-- EXP-70 包①：产品/服务分层快照（合同权威源 → 商务单/开票行快照）
-- 幂等：information_schema 判列/判索引后 PREPARE 加列；可重复执行
-- 范围：仅增量 DDL + 只读审计查询；不静默回填歧义历史（回填见包③）

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 商务单：产品类型快照（来自合同 product_type）
-- ---------------------------------------------------------------------------
SET @add_bo_product_type_snapshot = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_business_order'
          AND `column_name` = 'product_type_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_business_order` ADD COLUMN `product_type_snapshot` varchar(64) DEFAULT NULL COMMENT ''产品类型快照（来自合同 product_type）'' AFTER `product_name`'
);
PREPARE exp70_stmt FROM @add_bo_product_type_snapshot;
EXECUTE exp70_stmt;
DEALLOCATE PREPARE exp70_stmt;

-- ---------------------------------------------------------------------------
-- 2. 开票明细：来源合同 + 产品类型快照
-- ---------------------------------------------------------------------------
SET @add_inv_line_source_contract = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_invoice_application_line'
          AND `column_name` = 'source_contract_application_id'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_invoice_application_line` ADD COLUMN `source_contract_application_id` bigint DEFAULT NULL COMMENT ''来源合同签约申请 id（提交时从商务单复制）'' AFTER `business_order_id`'
);
PREPARE exp70_stmt FROM @add_inv_line_source_contract;
EXECUTE exp70_stmt;
DEALLOCATE PREPARE exp70_stmt;

SET @add_inv_line_product_type_snapshot = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_invoice_application_line'
          AND `column_name` = 'product_type_snapshot'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_invoice_application_line` ADD COLUMN `product_type_snapshot` varchar(64) DEFAULT NULL COMMENT ''产品类型快照（提交时从商务单复制）'' AFTER `source_contract_application_id`'
);
PREPARE exp70_stmt FROM @add_inv_line_product_type_snapshot;
EXECUTE exp70_stmt;
DEALLOCATE PREPARE exp70_stmt;

SET @add_idx_inv_line_source_contract = IF(
    EXISTS (
        SELECT 1 FROM `information_schema`.`statistics`
        WHERE `table_schema` = DATABASE() AND `table_name` = 'finance_invoice_application_line'
          AND `index_name` = 'idx_source_contract_application_id'
    ),
    'SELECT 1',
    'ALTER TABLE `finance_invoice_application_line` ADD KEY `idx_source_contract_application_id` (`source_contract_application_id`)'
);
PREPARE exp70_stmt FROM @add_idx_inv_line_source_contract;
EXECUTE exp70_stmt;
DEALLOCATE PREPARE exp70_stmt;

-- ---------------------------------------------------------------------------
-- 3. 只读审计（不改写数据；人工处置包③）
-- 用法：单独执行下列 SELECT，记录 count 与样例 id
-- ---------------------------------------------------------------------------

-- 3.1 APPROVED 合同产品为空
-- SELECT COUNT(*) AS cnt, GROUP_CONCAT(id ORDER BY id SEPARATOR ',') AS sample_ids
-- FROM (
--   SELECT id FROM finance_contract_application
--   WHERE deleted = b'0' AND approval_status = 'APPROVED' AND IFNULL(voided, b'0') = b'0'
--     AND (product_type IS NULL OR TRIM(product_type) = '')
--   ORDER BY id LIMIT 20
-- ) t;

-- 3.2 商务单无合同
-- SELECT COUNT(*) AS cnt, GROUP_CONCAT(id ORDER BY id SEPARATOR ',') AS sample_ids
-- FROM (
--   SELECT id FROM finance_business_order
--   WHERE deleted = b'0' AND contract_application_id IS NULL
--   ORDER BY id LIMIT 20
-- ) t;

-- 3.3 商务单已挂合同但快照空，且 product_name 与合同 product_type 不一致（歧义）
-- SELECT COUNT(*) AS cnt, GROUP_CONCAT(bo.id ORDER BY bo.id SEPARATOR ',') AS sample_ids
-- FROM (
--   SELECT bo.id
--   FROM finance_business_order bo
--   INNER JOIN finance_contract_application ca ON ca.id = bo.contract_application_id AND ca.deleted = b'0'
--   WHERE bo.deleted = b'0'
--     AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
--     AND (bo.product_name IS NULL OR TRIM(bo.product_name) = '' OR TRIM(bo.product_name) <> TRIM(ca.product_type))
--   ORDER BY bo.id LIMIT 20
-- ) t;

-- 3.4 同一开票申请内多产品（按商务单 COALESCE 快照/名称）
-- SELECT application_id, COUNT(DISTINCT product_key) AS product_cnt
-- FROM (
--   SELECT l.application_id,
--          COALESCE(NULLIF(TRIM(bo.product_type_snapshot), ''), NULLIF(TRIM(bo.product_name), ''), '') AS product_key
--   FROM finance_invoice_application_line l
--   INNER JOIN finance_business_order bo ON bo.id = l.business_order_id AND bo.deleted = b'0'
--   WHERE l.deleted = b'0'
-- ) x
-- GROUP BY application_id
-- HAVING COUNT(DISTINCT product_key) > 1
-- ORDER BY application_id
-- LIMIT 50;

-- 3.5 表头 tax_content 与可推导共同产品不一致（仅单产品单据）
-- SELECT a.id AS application_id, a.tax_content, derived.product_key
-- FROM finance_invoice_application a
-- INNER JOIN (
--   SELECT application_id,
--          MAX(product_key) AS product_key,
--          COUNT(DISTINCT product_key) AS product_cnt
--   FROM (
--     SELECT l.application_id,
--            COALESCE(NULLIF(TRIM(bo.product_type_snapshot), ''), NULLIF(TRIM(bo.product_name), ''), '') AS product_key
--     FROM finance_invoice_application_line l
--     INNER JOIN finance_business_order bo ON bo.id = l.business_order_id AND bo.deleted = b'0'
--     WHERE l.deleted = b'0'
--   ) y
--   GROUP BY application_id
--   HAVING COUNT(DISTINCT product_key) = 1
-- ) derived ON derived.application_id = a.id
-- WHERE a.deleted = b'0'
--   AND (
--     a.tax_content IS NULL OR TRIM(a.tax_content) = ''
--     OR TRIM(a.tax_content) <> derived.product_key
--   )
-- ORDER BY a.id
-- LIMIT 50;
