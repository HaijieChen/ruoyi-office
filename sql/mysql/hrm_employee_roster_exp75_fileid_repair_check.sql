-- EXP-75：历史 file_id 修复清单（只读，可重复执行）
-- 分类：missing / deleted / mismatch / ambiguous

-- 1) 重复 path（ambiguous path 候选）
SELECT 'ambiguous_path' AS issue, f.path AS key_val, COUNT(*) AS cnt,
       GROUP_CONCAT(f.id ORDER BY f.id) AS file_ids
FROM infra_file f
WHERE f.deleted = b'0' AND f.path IS NOT NULL AND f.path <> ''
GROUP BY f.path
HAVING COUNT(*) > 1;

-- 2) 重复 URL（ambiguous url 候选）
SELECT 'ambiguous_url' AS issue, f.url AS key_val, COUNT(*) AS cnt,
       GROUP_CONCAT(f.id ORDER BY f.id) AS file_ids
FROM infra_file f
WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
GROUP BY f.url
HAVING COUNT(*) > 1;

-- 3) 活动附件 file_id 指向不存在/软删（missing/deleted）
SELECT 'missing_or_deleted' AS issue, a.id, a.business_type, a.business_id,
       a.file_id, a.file_path, a.file_url
FROM common_attachment a
LEFT JOIN infra_file f ON f.id = a.file_id AND f.deleted = b'0'
WHERE a.business_type IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NOT NULL
  AND f.id IS NULL;

-- 4) 非空 file_id 与权威候选不一致（mismatch）
SELECT 'mismatch' AS issue, a.id, a.business_type, a.business_id,
       a.file_id, a.file_path, a.file_url,
       (SELECT MIN(f.id) FROM infra_file f
         WHERE f.deleted = b'0' AND f.url = a.file_url
         GROUP BY f.url HAVING COUNT(*) = 1) AS auth_url_id,
       (SELECT MIN(f.id) FROM infra_file f
         WHERE f.deleted = b'0' AND f.path = a.file_path
         GROUP BY f.path HAVING COUNT(*) = 1) AS auth_path_id
FROM common_attachment a
WHERE a.business_type IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NOT NULL
  AND NOT (
    EXISTS (
      SELECT 1 FROM (
        SELECT f.url AS u, MIN(f.id) AS fid FROM infra_file f
        WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
        GROUP BY f.url HAVING COUNT(*) = 1
      ) c WHERE c.u = a.file_url AND c.fid = a.file_id
    )
    OR (
      NOT EXISTS (
        SELECT 1 FROM (
          SELECT f.url AS u FROM infra_file f
          WHERE f.deleted = b'0' AND f.url IS NOT NULL AND f.url <> ''
          GROUP BY f.url HAVING COUNT(*) = 1
        ) c WHERE c.u = a.file_url
      )
      AND EXISTS (
        SELECT 1 FROM (
          SELECT f.path AS p, MIN(f.id) AS fid FROM infra_file f
          WHERE f.deleted = b'0' AND f.path IS NOT NULL AND f.path <> ''
          GROUP BY f.path HAVING COUNT(*) = 1
        ) c WHERE c.p = a.file_path AND c.fid = a.file_id
      )
    )
  );

-- 5) 仍无 file_id：歧义或无候选（ambiguous / missing）
SELECT 'null_unresolved' AS issue, a.id, a.business_type, a.business_id,
       a.file_path, a.file_url, a.file_id, a.tenant_id,
       (SELECT COUNT(*) FROM infra_file f WHERE f.deleted = b'0' AND f.path = a.file_path) AS path_cnt,
       (SELECT COUNT(*) FROM infra_file f WHERE f.deleted = b'0' AND f.url = a.file_url) AS url_cnt
FROM common_attachment a
WHERE a.business_type IN ('hrm_employee_archive_onboarding', '201')
  AND a.deleted = b'0'
  AND a.file_id IS NULL;
