-- EXP-70 包③（第四轮复审修订）：无歧义回填 + 只读审计
-- 前置：finance_product_snapshot_exp70.sql
-- 幂等：仅写空字段；已有非空不覆盖
-- 原则：
--   · 商务单 snapshot 仅从「权威合同」补齐，条件与正常写路径一致（复审 #5）：
--       APPROVED + 未作废 + importer_id = applicant_user_id + 产品非空 + name 无歧义
--   · 历史开票行产品/来源合同：无行级不可变证据时保持 NULL
--   · 表头 tax_content 仅当全部行已有行级 snapshot 且同产品且表头空时回填
--   · 不满足权威条件的关联 → 审计 I，不得自动 UPDATE

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 商务单：product_type_snapshot ← 合同 product_type（权威条件 = 正常写路径）
-- ---------------------------------------------------------------------------
UPDATE finance_business_order bo
INNER JOIN finance_contract_application ca
    ON ca.id = bo.contract_application_id
   AND ca.deleted = b'0'
SET bo.product_type_snapshot = TRIM(ca.product_type),
    bo.update_time = NOW()
WHERE bo.deleted = b'0'
  AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
  AND ca.approval_status = 'APPROVED'
  AND IFNULL(ca.voided, b'0') = b'0'
  AND ca.product_type IS NOT NULL
  AND TRIM(ca.product_type) <> ''
  AND bo.importer_id = ca.applicant_user_id
  AND (
        bo.product_name IS NULL
     OR TRIM(bo.product_name) = ''
     OR TRIM(bo.product_name) = TRIM(ca.product_type)
  );

-- 仅当 name 空且已有 snapshot 时双写 legacy（不覆盖自由文本）
UPDATE finance_business_order
SET product_name = product_type_snapshot,
    update_time = NOW()
WHERE deleted = b'0'
  AND product_type_snapshot IS NOT NULL
  AND TRIM(product_type_snapshot) <> ''
  AND (product_name IS NULL OR TRIM(product_name) = '');

-- ---------------------------------------------------------------------------
-- 2. 历史开票行 product_type_snapshot / source_contract：不自动回填
--    P1 #1：无行级不可变证据时保持 NULL（见审计 H）
--    P1 原 #5：不倒推当前 BO 合同（见审计 G）
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 3. 开票表头 tax_content：仅全部行「已有行级 snapshot」且同产品、表头为空
--    绝不从 BO 当前产品推导（P1 #1）
-- ---------------------------------------------------------------------------
UPDATE finance_invoice_application a
INNER JOIN (
    SELECT
        application_id,
        MAX(line_snapshot) AS common_product,
        COUNT(DISTINCT line_snapshot) AS product_cnt,
        SUM(CASE WHEN line_snapshot IS NULL OR line_snapshot = '' THEN 1 ELSE 0 END) AS empty_lines
    FROM (
        SELECT
            l.application_id,
            CASE
                WHEN l.product_type_snapshot IS NOT NULL AND TRIM(l.product_type_snapshot) <> ''
                    THEN TRIM(l.product_type_snapshot)
                ELSE NULL
            END AS line_snapshot
        FROM finance_invoice_application_line l
        WHERE l.deleted = b'0'
    ) x
    GROUP BY application_id
    HAVING product_cnt = 1
       AND empty_lines = 0
       AND common_product IS NOT NULL
       AND common_product <> ''
) d ON d.application_id = a.id
SET a.tax_content = d.common_product,
    a.update_time = NOW()
WHERE a.deleted = b'0'
  AND (a.tax_content IS NULL OR TRIM(a.tax_content) = '');

-- ---------------------------------------------------------------------------
-- A–H. 只读人工清单：完整 COUNT 与样例分离
-- ---------------------------------------------------------------------------

-- A. APPROVED 合同产品为空
SELECT 'A_contract_product_empty' AS audit_key,
       (SELECT COUNT(*) FROM finance_contract_application
        WHERE deleted = b'0' AND approval_status = 'APPROVED'
          AND IFNULL(voided, b'0') = b'0'
          AND (product_type IS NULL OR TRIM(product_type) = '')) AS cnt,
       (SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',')
        FROM (
            SELECT id FROM finance_contract_application
            WHERE deleted = b'0' AND approval_status = 'APPROVED'
              AND IFNULL(voided, b'0') = b'0'
              AND (product_type IS NULL OR TRIM(product_type) = '')
            ORDER BY id LIMIT 20
        ) s) AS sample_ids;

-- B. 商务单无合同
SELECT 'B_bo_no_contract' AS audit_key,
       (SELECT COUNT(*) FROM finance_business_order
        WHERE deleted = b'0' AND contract_application_id IS NULL) AS cnt,
       (SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',')
        FROM (
            SELECT id FROM finance_business_order
            WHERE deleted = b'0' AND contract_application_id IS NULL
            ORDER BY id LIMIT 20
        ) s) AS sample_ids;

-- C. 商务单 snapshot 空且 product_name 与合同产品冲突
SELECT 'C_bo_snapshot_conflict' AS audit_key,
       (SELECT COUNT(*)
        FROM finance_business_order bo
        INNER JOIN finance_contract_application ca
            ON ca.id = bo.contract_application_id AND ca.deleted = b'0'
        WHERE bo.deleted = b'0'
          AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
          AND ca.product_type IS NOT NULL AND TRIM(ca.product_type) <> ''
          AND bo.product_name IS NOT NULL AND TRIM(bo.product_name) <> ''
          AND TRIM(bo.product_name) <> TRIM(ca.product_type)) AS cnt,
       (SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',')
        FROM (
            SELECT bo.id
            FROM finance_business_order bo
            INNER JOIN finance_contract_application ca
                ON ca.id = bo.contract_application_id AND ca.deleted = b'0'
            WHERE bo.deleted = b'0'
              AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
              AND ca.product_type IS NOT NULL AND TRIM(ca.product_type) <> ''
              AND bo.product_name IS NOT NULL AND TRIM(bo.product_name) <> ''
              AND TRIM(bo.product_name) <> TRIM(ca.product_type)
            ORDER BY bo.id LIMIT 20
        ) s) AS sample_ids;

-- D. 同一开票申请多「行级 snapshot」产品（完整 count）
SELECT 'D_invoice_mixed_products' AS audit_key,
       (SELECT COUNT(*) FROM (
            SELECT application_id
            FROM (
                SELECT l.application_id,
                       CASE
                           WHEN l.product_type_snapshot IS NOT NULL AND TRIM(l.product_type_snapshot) <> ''
                               THEN TRIM(l.product_type_snapshot)
                           ELSE NULL
                       END AS line_snapshot
                FROM finance_invoice_application_line l
                WHERE l.deleted = b'0'
            ) x
            GROUP BY application_id
            HAVING COUNT(DISTINCT line_snapshot) > 1
        ) mixed) AS cnt,
       (SELECT GROUP_CONCAT(application_id ORDER BY application_id SEPARATOR ',')
        FROM (
            SELECT application_id
            FROM (
                SELECT l.application_id,
                       CASE
                           WHEN l.product_type_snapshot IS NOT NULL AND TRIM(l.product_type_snapshot) <> ''
                               THEN TRIM(l.product_type_snapshot)
                           ELSE NULL
                       END AS line_snapshot
                FROM finance_invoice_application_line l
                WHERE l.deleted = b'0'
            ) x
            GROUP BY application_id
            HAVING COUNT(DISTINCT line_snapshot) > 1
            ORDER BY application_id
            LIMIT 50
        ) s) AS sample_ids;

-- E. 表头 tax_content 与「全部行已有 snapshot 且同产品」推导值冲突
--    复审 #1：行快照完整同产品时，空/NULL 表头亦计入不一致（不得 E=0 误放行）
SELECT 'E_tax_content_mismatch' AS audit_key,
       (SELECT COUNT(*)
        FROM finance_invoice_application a
        INNER JOIN (
            SELECT application_id,
                   MAX(line_snapshot) AS product_key,
                   SUM(CASE WHEN line_snapshot IS NULL OR line_snapshot = '' THEN 1 ELSE 0 END) AS empty_lines
            FROM (
                SELECT l.application_id,
                       CASE
                           WHEN l.product_type_snapshot IS NOT NULL AND TRIM(l.product_type_snapshot) <> ''
                               THEN TRIM(l.product_type_snapshot)
                           ELSE NULL
                       END AS line_snapshot
                FROM finance_invoice_application_line l
                WHERE l.deleted = b'0'
            ) y
            GROUP BY application_id
            HAVING COUNT(DISTINCT line_snapshot) = 1
               AND SUM(CASE WHEN line_snapshot IS NULL OR line_snapshot = '' THEN 1 ELSE 0 END) = 0
               AND MAX(line_snapshot) IS NOT NULL
        ) d ON d.application_id = a.id
        WHERE a.deleted = b'0'
          AND (
                a.tax_content IS NULL OR TRIM(a.tax_content) = ''
             OR TRIM(a.tax_content) <> d.product_key
          )) AS cnt,
       (SELECT GROUP_CONCAT(application_id ORDER BY application_id SEPARATOR ',')
        FROM (
            SELECT a.id AS application_id
            FROM finance_invoice_application a
            INNER JOIN (
                SELECT application_id, MAX(line_snapshot) AS product_key
                FROM (
                    SELECT l.application_id,
                           CASE
                               WHEN l.product_type_snapshot IS NOT NULL AND TRIM(l.product_type_snapshot) <> ''
                                   THEN TRIM(l.product_type_snapshot)
                               ELSE NULL
                           END AS line_snapshot
                    FROM finance_invoice_application_line l
                    WHERE l.deleted = b'0'
                ) y
                GROUP BY application_id
                HAVING COUNT(DISTINCT line_snapshot) = 1
                   AND SUM(CASE WHEN line_snapshot IS NULL OR line_snapshot = '' THEN 1 ELSE 0 END) = 0
                   AND MAX(line_snapshot) IS NOT NULL
            ) d ON d.application_id = a.id
            WHERE a.deleted = b'0'
              AND (
                    a.tax_content IS NULL OR TRIM(a.tax_content) = ''
                 OR TRIM(a.tax_content) <> d.product_key
              )
            ORDER BY a.id LIMIT 20
        ) s) AS sample_ids;

-- F_bo. 可开票口径：有合同 + 可开余额 > 0 + snapshot 仍空 + 权威合同有效
--    复审 #2：与清单「可开票」一致；权威无效行归 I，避免与 I 例外永久冲突
SELECT 'F_bo_snapshot_still_empty_with_contract' AS audit_key,
       (SELECT COUNT(*)
        FROM finance_business_order bo
        INNER JOIN finance_contract_application ca
            ON ca.id = bo.contract_application_id AND ca.deleted = b'0'
        WHERE bo.deleted = b'0'
          AND bo.contract_application_id IS NOT NULL
          AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
          AND bo.settlement_amount > IFNULL(bo.invoiced_occupied_amount, 0)
          AND ca.approval_status = 'APPROVED'
          AND IFNULL(ca.voided, b'0') = b'0'
          AND bo.importer_id IS NOT NULL
          AND ca.applicant_user_id IS NOT NULL
          AND bo.importer_id = ca.applicant_user_id
          AND ca.product_type IS NOT NULL
          AND TRIM(ca.product_type) <> '') AS cnt,
       (SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',')
        FROM (
            SELECT bo.id
            FROM finance_business_order bo
            INNER JOIN finance_contract_application ca
                ON ca.id = bo.contract_application_id AND ca.deleted = b'0'
            WHERE bo.deleted = b'0'
              AND bo.contract_application_id IS NOT NULL
              AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
              AND bo.settlement_amount > IFNULL(bo.invoiced_occupied_amount, 0)
              AND ca.approval_status = 'APPROVED'
              AND IFNULL(ca.voided, b'0') = b'0'
              AND bo.importer_id IS NOT NULL
              AND ca.applicant_user_id IS NOT NULL
              AND bo.importer_id = ca.applicant_user_id
              AND ca.product_type IS NOT NULL
              AND TRIM(ca.product_type) <> ''
            ORDER BY bo.id LIMIT 20
        ) s) AS sample_ids;

SELECT 'F_line_snapshot_still_empty' AS audit_key,
       (SELECT COUNT(*) FROM finance_invoice_application_line l
        WHERE l.deleted = b'0'
          AND (l.product_type_snapshot IS NULL OR TRIM(l.product_type_snapshot) = '')) AS cnt,
       (SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',')
        FROM (
            SELECT id FROM finance_invoice_application_line
            WHERE deleted = b'0'
              AND (product_type_snapshot IS NULL OR TRIM(product_type_snapshot) = '')
            ORDER BY id LIMIT 20
        ) s) AS sample_ids;

-- G. 开票行来源合同不可证实
SELECT 'G_line_source_contract_unproven' AS audit_key,
       (SELECT COUNT(*) FROM finance_invoice_application_line l
        WHERE l.deleted = b'0'
          AND l.source_contract_application_id IS NULL) AS cnt,
       (SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',')
        FROM (
            SELECT id FROM finance_invoice_application_line
            WHERE deleted = b'0' AND source_contract_application_id IS NULL
            ORDER BY id LIMIT 20
        ) s) AS sample_ids;

-- H. 开票行产品不可证实（P1 #1：无行级 snapshot，不得用当前 BO 伪装）
SELECT 'H_line_product_unproven' AS audit_key,
       (SELECT COUNT(*) FROM finance_invoice_application_line l
        WHERE l.deleted = b'0'
          AND (l.product_type_snapshot IS NULL OR TRIM(l.product_type_snapshot) = '')) AS cnt,
       (SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',')
        FROM (
            SELECT id FROM finance_invoice_application_line
            WHERE deleted = b'0'
              AND (product_type_snapshot IS NULL OR TRIM(product_type_snapshot) = '')
            ORDER BY id LIMIT 20
        ) s) AS sample_ids;

-- I. 商务单已挂合同但合同不满足正常写路径权威条件（不得自动回填）
--    复审 #3：LEFT JOIN + NULL-safe；覆盖缺失/已删除合同与 NULL 状态/importer/applicant
SELECT 'I_bo_contract_authority_invalid' AS audit_key,
       (SELECT COUNT(*)
        FROM finance_business_order bo
        LEFT JOIN finance_contract_application ca
            ON ca.id = bo.contract_application_id AND ca.deleted = b'0'
        WHERE bo.deleted = b'0'
          AND bo.contract_application_id IS NOT NULL
          AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
          AND (
                ca.id IS NULL
             OR ca.approval_status IS NULL
             OR ca.approval_status <> 'APPROVED'
             OR IFNULL(ca.voided, b'0') = b'1'
             OR bo.importer_id IS NULL
             OR ca.applicant_user_id IS NULL
             OR bo.importer_id <> ca.applicant_user_id
             OR ca.product_type IS NULL
             OR TRIM(ca.product_type) = ''
          )) AS cnt,
       (SELECT GROUP_CONCAT(id ORDER BY id SEPARATOR ',')
        FROM (
            SELECT bo.id
            FROM finance_business_order bo
            LEFT JOIN finance_contract_application ca
                ON ca.id = bo.contract_application_id AND ca.deleted = b'0'
            WHERE bo.deleted = b'0'
              AND bo.contract_application_id IS NOT NULL
              AND (bo.product_type_snapshot IS NULL OR TRIM(bo.product_type_snapshot) = '')
              AND (
                    ca.id IS NULL
                 OR ca.approval_status IS NULL
                 OR ca.approval_status <> 'APPROVED'
                 OR IFNULL(ca.voided, b'0') = b'1'
                 OR bo.importer_id IS NULL
                 OR ca.applicant_user_id IS NULL
                 OR bo.importer_id <> ca.applicant_user_id
                 OR ca.product_type IS NULL
                 OR TRIM(ca.product_type) = ''
              )
            ORDER BY bo.id LIMIT 20
        ) s) AS sample_ids;

-- 结束：A–I 人工处置；权威不满足者不得静默写入

-- 结束：A–H 人工处置；历史行产品/来源无证明保持 NULL
